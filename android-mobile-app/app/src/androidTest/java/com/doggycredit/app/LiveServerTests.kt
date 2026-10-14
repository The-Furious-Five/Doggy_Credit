package com.doggycredit.app

import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.auth.userFromToken
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Solo lectura: no crea usuarios, consentimientos, evaluaciones ni resoluciones en el VPS. */
class LiveServerTests {
    @Test fun deployedOidcAndMobilePkceAreAvailableFromAndroid() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTests") == "true")
        val client = networkClient()
        client.execute(Request.Builder().url("${Server.ISSUER}/.well-known/openid-configuration").build()).use {
            assertEquals(200, it.code)
            val metadata = ApiJson.parseToJsonElement(it.body!!.string()).jsonObject
            assertEquals(Server.ISSUER, metadata["issuer"]!!.jsonPrimitive.content)
        }
        val url = "${Server.ISSUER}/protocol/openid-connect/auth?client_id=${Server.CLIENT}&redirect_uri=doggycredit%3A%2F%2Foauth%2Fcallback&response_type=code&scope=openid%20profile%20email%20roles&state=android-connectivity&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256"
        client.execute(Request.Builder().url(url).build()).use { assertEquals(200, it.code); assertTrue(it.body!!.string().contains("kc-form-login")) }
    }
    @Test fun deployedApiRequiresAuthentication() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTests") == "true")
        networkClient().execute(Request.Builder().url("${Server.API}tenant/actual").build()).use {
            assertEquals(401, it.code)
            val problem = ApiJson.decodeFromString<Problem>(it.body!!.string())
            assertEquals("UNAUTHENTICATED", problem.code)
        }
    }
    @Test fun callbackIsRegisteredToNativeReceiver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(Server.REDIRECT)).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.packageName)
        assertNotNull(context.packageManager.resolveActivity(intent, 0))
    }
    @Test fun allAuthenticatedReadFlowsWithInstitutionToken(): Unit = runBlocking {
        val token = InstrumentationRegistry.getArguments().getString("accessToken")
        assumeTrue("Requiere un token real proporcionado temporalmente", !token.isNullOrBlank())
        val user = userFromToken(token!!)
        val api = LiveDoggyCreditApi(object : AccessTokens {
            override suspend fun accessToken(rejectedToken: String?) = token
            override suspend fun reject(token: String) {}
        })
        assertEquals(user.tenantId.lowercase(), api.tenant().id.lowercase())
        api.usage(); api.preferences(); api.integrations()
        val today = java.time.LocalDate.now(Display.zone)
        api.analytics(today.withDayOfMonth(1).toString(), today.toString())
        if (user.canResolve) api.reviewCases()
        api.applicants().items.firstOrNull()?.let { client ->
            api.applicant(client.id); api.consents(client.id); api.assessments(client.id)
            assertEquals(client.id, api.applicants(size = 1, document = client.document).items.firstOrNull()?.id)
        }
        api.assessments().items.firstOrNull()?.let { api.assessment(it.id) }
    }
}
