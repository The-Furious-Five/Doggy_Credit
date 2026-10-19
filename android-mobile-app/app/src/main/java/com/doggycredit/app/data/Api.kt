package com.doggycredit.app.data

import com.doggycredit.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Server {
    const val ORIGIN = "https://doggycredit.los5furiosos.cloud"
    const val API = "$ORIGIN/api/v1/"
    const val ISSUER = "$ORIGIN/auth/realms/doggycredit"
    const val CLIENT = "doggycredit-mobile"
    const val REDIRECT = "doggycredit://oauth/callback"
    const val CONSOLE = "$ORIGIN/app"
}
@Serializable data class Problem(val title: String, val status: Int, val detail: String? = null, val code: String, val correlationId: String? = null)
class ApiException(val status: Int = 0, val code: String? = null, override val message: String, val correlationId: String? = null) : IOException(message) {
    companion object {
        fun response(status: Int, body: String): ApiException {
            val problem = runCatching { ApiJson.decodeFromString<Problem>(body) }.getOrNull()
            return ApiException(status, problem?.code, problem?.detail ?: problem?.title ?: when (status) {
                401 -> "Tu sesión venció. Inicia sesión de nuevo."
                403 -> "Tu rol no tiene permiso para esta acción."
                404 -> "El registro no existe o pertenece a otra institución."
                429 -> "Se alcanzó el límite de solicitudes. Intenta de nuevo más tarde."
                else -> "El servidor no pudo completar la solicitud ($status). Vuelve a intentarlo."
            }, problem?.correlationId)
        }
    }
}
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message + (correlationId?.let { "\nCódigo de seguimiento: $it" } ?: "")
    is java.net.SocketTimeoutException -> "El servidor tardó demasiado. Vuelve a intentarlo; tu borrador se conserva."
    is javax.net.ssl.SSLException -> "No se pudo verificar la conexión segura con el servidor."
    is IOException -> "No pudimos conectarnos. Revisa tu conexión y vuelve a intentarlo."
    else -> "No pudimos interpretar la respuesta del servidor. Vuelve a intentarlo."
}
interface AccessTokens {
    suspend fun accessToken(rejectedToken: String? = null): String
    suspend fun reject(token: String)
}
fun networkClient(): OkHttpClient = OkHttpClient.Builder().cache(null)
    .connectTimeout(20, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).callTimeout(60, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

suspend fun OkHttpClient.execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, value, _ -> value.close() }
        }
    })
}

interface DoggyCreditApi {
    suspend fun tenant(): Tenant
    suspend fun usage(): TenantUsage
    suspend fun preferences(): Preferences
    suspend fun applicants(page: Int = 1, size: Int = 25, segment: String? = null, document: String? = null): Page<Applicant>
    suspend fun createApplicant(body: NewApplicant): Applicant
    suspend fun applicant(id: String): Applicant
    suspend fun consents(applicantId: String): List<Consent>
    suspend fun createConsent(body: NewConsent): Consent
    suspend fun revokeConsent(id: String): Consent
    suspend fun integrations(): List<Integration>
    suspend fun assessments(applicantId: String? = null, page: Int = 1, size: Int = 20, since: String? = null): Page<AssessmentSummary>
    suspend fun createAssessment(body: NewAssessment, key: String): Assessment
    suspend fun assessment(id: String): Assessment
    suspend fun reviewCases(page: Int = 1, size: Int = 20): ReviewPage
    suspend fun resolve(id: String, body: NewResolution): Resolution
    suspend fun analytics(from: String, to: String): AnalyticsSummary
}

class LiveDoggyCreditApi(private val tokens: AccessTokens, private val client: OkHttpClient = networkClient(), baseUrl: String = Server.API) : DoggyCreditApi {
    private val base = baseUrl.toHttpUrl()
    private suspend inline fun <reified T> request(path: String, method: String = "GET", body: String? = null,
                                                 query: Map<String, String?> = emptyMap(), headers: Map<String, String> = emptyMap()): T {
        val text = rawRequest(path, method, body, query, headers)
        return try { ApiJson.decodeFromString<T>(text) }
        catch (error: CancellationException) { throw error }
        catch (_: Exception) { throw ApiException(message = "La respuesta del servidor no coincide con el contrato esperado.") }
    }
    private suspend fun rawRequest(path: String, method: String, body: String?, query: Map<String, String?>, headers: Map<String, String>): String {
        val url = base.newBuilder().addPathSegments(path).apply { query.forEach { (key, value) -> if (value != null) addQueryParameter(key, value) } }.build()
        fun build(token: String) = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .header("Accept", "application/json, application/problem+json").header("Cache-Control", "no-store")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .method(method, if (method == "GET") null else (body ?: "").toRequestBody("application/json".toMediaType())).build()
        var token = tokens.accessToken()
        var result = client.execute(build(token))
        if (result.code == 401) {
            result.close()
            token = tokens.accessToken(rejectedToken = token)
            result = client.execute(build(token))
        }
        return result.use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) tokens.reject(token)
            if (!response.isSuccessful) throw ApiException.response(response.code, text)
            text
        }
    }
    override suspend fun tenant(): Tenant = request("tenant/actual")
    override suspend fun usage(): TenantUsage = request("tenant/actual/uso")
    override suspend fun preferences(): Preferences = request("tenant/actual/preferencias")
    override suspend fun applicants(page: Int, size: Int, segment: String?, document: String?): Page<Applicant> =
        request("solicitantes", query = mapOf("pagina" to "$page", "tamano" to "$size", "segmento" to segment), headers = document?.let { mapOf("X-Documento" to it) } ?: emptyMap())
    override suspend fun createApplicant(body: NewApplicant): Applicant = request("solicitantes", "POST", ApiJson.encodeToString(body))
    override suspend fun applicant(id: String): Applicant = request("solicitantes/$id")
    override suspend fun consents(applicantId: String): List<Consent> = request("consentimientos", query = mapOf("solicitanteId" to applicantId))
    override suspend fun createConsent(body: NewConsent): Consent = request("consentimientos", "POST", ApiJson.encodeToString(body))
    override suspend fun revokeConsent(id: String): Consent = request("consentimientos/$id/revocar", "POST")
    override suspend fun integrations(): List<Integration> = request("integraciones")
    override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?): Page<AssessmentSummary> = request("credit-assessments", query = mapOf("solicitanteId" to applicantId, "pagina" to "$page", "tamano" to "$size", "desde" to since))
    override suspend fun createAssessment(body: NewAssessment, key: String): Assessment = request("credit-assessments", "POST", ApiJson.encodeToString(body), headers = mapOf("Idempotency-Key" to key))
    override suspend fun assessment(id: String): Assessment = request("credit-assessments/$id")
    override suspend fun reviewCases(page: Int, size: Int): ReviewPage = request("revision-manual", query = mapOf("pagina" to "$page", "tamano" to "$size"))
    override suspend fun resolve(id: String, body: NewResolution): Resolution = request("revision-manual/$id/resolucion", "POST", ApiJson.encodeToString(body))
    override suspend fun analytics(from: String, to: String): AnalyticsSummary = request("analytics/resumen", query = mapOf("desde" to from, "hasta" to to))
}
