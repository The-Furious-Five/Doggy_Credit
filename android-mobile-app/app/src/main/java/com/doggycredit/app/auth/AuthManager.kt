package com.doggycredit.app.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.openid.appauth.*
import okhttp3.FormBody
import okhttp3.Request
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Claims para presentación; la API valida firma, permisos y aislamiento por institución. */
fun userFromToken(token: String): UserSession {
    val payload = token.split('.').getOrNull(1) ?: error("Token inválido")
    val claims = ApiJson.parseToJsonElement(Base64.getUrlDecoder().decode(payload).decodeToString()).jsonObject
    fun text(key: String) = claims[key]?.jsonPrimitive?.contentOrNull
    require(text("iss") == Server.ISSUER) { "El token pertenece a otro servidor" }
    val audience = claims["aud"]
    val audiences = if (audience is JsonArray) audience.map { it.jsonPrimitive.content } else listOfNotNull(audience?.jsonPrimitive?.contentOrNull)
    require("doggycredit-api" in audiences) { "La cuenta no tiene acceso a la API" }
    val tenant = text("tenant_id") ?: error("Tu cuenta no tiene una institución asignada")
    UUID.fromString(tenant)
    val username = text("preferred_username").orEmpty()
    val roles = claims["realm_access"]?.jsonObject?.get("roles")?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        .filter { it in setOf("ANALYST", "TENANT_ADMIN", "PLATFORM_ADMIN", "API_CLIENT") }
    return UserSession(text("name")?.takeIf(String::isNotBlank) ?: username.ifBlank { "Usuario" }, username, tenant, roles)
}

interface SessionController : AccessTokens {
    val user: StateFlow<UserSession?>
    fun authorizationIntent(): Intent
    suspend fun complete(data: Intent): SessionTokens?
    suspend fun restore()
    suspend fun accept(value: SessionTokens)
    suspend fun signOut()
}

class AuthManager(context: Context, private val storage: SessionStorage = SecureSessionStore(context)) : SessionController {
    private val service = AuthorizationService(context)
    private val http = networkClient()
    private val mutex = Mutex()
    private var tokens: SessionTokens? = null
    private val _user = MutableStateFlow<UserSession?>(null)
    override val user = _user.asStateFlow()
    private val configuration = AuthorizationServiceConfiguration(
        Uri.parse("${Server.ISSUER}/protocol/openid-connect/auth"), Uri.parse("${Server.ISSUER}/protocol/openid-connect/token")
    )
    override fun authorizationIntent(): Intent {
        // AppAuth genera el verifier S256, valida state y conserva el flujo entre actividades.
        val request = AuthorizationRequest.Builder(configuration, Server.CLIENT, ResponseTypeValues.CODE, Uri.parse(Server.REDIRECT))
            .setScopes("openid", "profile", "email", "roles").setPrompt("login").build()
        return service.getAuthorizationRequestIntent(request)
    }
    override suspend fun complete(data: Intent): SessionTokens? {
        val response = AuthorizationResponse.fromIntent(data)
        val error = AuthorizationException.fromIntent(data)
        if (error?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code) return null
        if (error != null) throw ApiException(message = error.errorDescription ?: "No pudimos iniciar sesión con tu institución.")
        requireNotNull(response) { "No se recibió una respuesta de autorización" }
        val result = suspendCancellableCoroutine<TokenResponse> { continuation ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenError ->
                if (continuation.isActive) {
                    if (tokenResponse != null) continuation.resume(tokenResponse)
                    else continuation.resumeWithException(tokenError ?: ApiException(message = "No se recibieron credenciales del servidor."))
                }
            }
        }
        val token = requireNotNull(result.accessToken)
        userFromToken(token)
        return SessionTokens(token, result.refreshToken, result.idToken, result.accessTokenExpirationTime ?: System.currentTimeMillis())
    }
    override suspend fun restore() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = storage.read() ?: return@withLock
            try { val user = userFromToken(saved.accessToken); tokens = saved; _user.value = user }
            catch (_: Exception) { storage.clear() }
        }
    }
    override suspend fun accept(value: SessionTokens) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val user = userFromToken(value.accessToken)
            storage.write(value)
            tokens = value
            _user.value = user
        }
    }
    override suspend fun accessToken(rejectedToken: String?): String = mutex.withLock {
        val current = tokens ?: throw ApiException(401, "UNAUTHENTICATED", "Inicia sesión con tu institución para continuar.")
        if (System.currentTimeMillis() + 30_000 < current.expiresAt && (rejectedToken == null || rejectedToken != current.accessToken)) return@withLock current.accessToken
        val refresh = current.refreshToken ?: run { clearLocked(); throw ApiException.response(401, "") }
        val body = FormBody.Builder().add("grant_type", "refresh_token").add("client_id", Server.CLIENT).add("refresh_token", refresh).build()
        val request = Request.Builder().url("${Server.ISSUER}/protocol/openid-connect/token").post(body).build()
        http.execute(request).use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code in listOf(400, 401, 403)) clearLocked()
                throw ApiException.response(if (response.code in listOf(400, 401, 403)) 401 else response.code, raw)
            }
            val renewed = ApiJson.decodeFromString<RefreshResponse>(raw)
            val user = userFromToken(renewed.accessToken)
            val next = SessionTokens(renewed.accessToken, renewed.refreshToken ?: refresh, renewed.idToken ?: current.idToken,
                System.currentTimeMillis() + renewed.expiresIn * 1000L)
            withContext(Dispatchers.IO) { storage.write(next) }
            tokens = next
            _user.value = user
            next.accessToken
        }
    }
    override suspend fun reject(token: String) { mutex.withLock { if (tokens?.accessToken == token) clearLocked() } }
    override suspend fun signOut() {
        val refresh = mutex.withLock { val refresh = tokens?.refreshToken; clearLocked(); refresh }
        if (refresh != null) runCatching {
            val body = FormBody.Builder().add("client_id", Server.CLIENT).add("refresh_token", refresh).build()
            http.execute(Request.Builder().url("${Server.ISSUER}/protocol/openid-connect/logout").post(body).build()).close()
        }
    }
    private fun clearLocked() { tokens = null; storage.clear(); _user.value = null }
    @Serializable private data class RefreshResponse(
        @SerialName("access_token") val accessToken: String, @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("id_token") val idToken: String? = null, @SerialName("expires_in") val expiresIn: Long
    )
}
