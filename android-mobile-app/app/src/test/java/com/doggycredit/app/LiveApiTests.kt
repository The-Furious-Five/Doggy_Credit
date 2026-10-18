package com.doggycredit.app

import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LiveApiTests {
    private lateinit var server: MockWebServer
    private lateinit var api: DoggyCreditApi
    private val tokens = TestTokens()
    @Before fun setup() { server = MockWebServer(); server.start(); api = LiveDoggyCreditApi(tokens, baseUrl = server.url("/api/v1/").toString()) }
    @After fun close() { server.shutdown() }
    private fun respond(body: String, status: Int = 200) { server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body)) }
    @Test fun exactSearchKeepsDocumentOutOfUrl() = runTest {
        respond("""{"items":[$APPLICANT],"total":1}""")
        val page = api.applicants(page = 2, size = 25, segment = "PERSONA", document = "CLIENT-001")
        val request = server.takeRequest()
        assertEquals("Bearer original", request.getHeader("Authorization")); assertEquals("CLIENT-001", request.getHeader("X-Documento"))
        assertEquals("no-store", request.getHeader("Cache-Control")); assertFalse(request.path!!.contains("CLIENT"))
        assertEquals("2", request.requestUrl!!.queryParameter("pagina")); assertEquals("25", request.requestUrl!!.queryParameter("tamano"))
        assertEquals("PERSONA", request.requestUrl!!.queryParameter("segmento")); assertEquals(1, page.total)
    }
    @Test fun tenantUsagePreferencesAndSourcesUseLiveRoutes() = runTest {
        respond("""{"id":"t1","nombre":"Institution","nit":"1","tipo":"IMF","plan":"FREE","pais":"BO","moneda":"BOB","activo":true}""")
        assertEquals("t1", api.tenant().id); assertEquals("/api/v1/tenant/actual", server.takeRequest().path)
        respond("""{"evaluacionesMes":123,"limiteMensual":500}"""); assertEquals(123, api.usage().used); assertEquals("/api/v1/tenant/actual/uso", server.takeRequest().path)
        respond("""{"enmascararDatos":true}"""); assertTrue(api.preferences().maskSensitiveData); assertEquals("/api/v1/tenant/actual/preferencias", server.takeRequest().path)
        respond("[]"); assertTrue(api.integrations().isEmpty()); assertEquals("/api/v1/integraciones", server.takeRequest().path)
    }
    @Test fun consentAndApplicantMutationsMatchContract() = runTest {
        respond(APPLICANT, 201)
        api.createApplicant(NewApplicant("CLIENT-001", "Comercio", 24, "5000.00", purpose = "Capital de trabajo", segment = "PERSONA"))
        server.takeRequest().let { assertEquals("POST", it.method); assertEquals("/api/v1/solicitantes", it.path); assertTrue(it.body.readUtf8().contains("ingresosMensuales")) }
        respond(CONSENT, 201); assertEquals("c1", api.createConsent(NewConsent("CLIENT-001", listOf("BURO"))).id)
        server.takeRequest().let { assertEquals("/api/v1/consentimientos", it.path); assertTrue(it.body.readUtf8().contains("\"fuentes\":[\"BURO\"]")) }
        respond("[$CONSENT]"); api.consents("a1"); assertEquals("a1", server.takeRequest().requestUrl!!.queryParameter("solicitanteId"))
        respond(CONSENT.replace("ACTIVO", "REVOCADO")); assertEquals("REVOCADO", api.revokeConsent("c1").status); assertEquals("/api/v1/consentimientos/c1/revocar", server.takeRequest().path)
    }
    @Test fun unauthorizedRetriesSameAssessmentWithStableIdempotency() = runTest {
        respond("{}", 401); respond("""{"score":710,"banda":"B","simulado":false}""", 201)
        val result = api.createAssessment(NewAssessment("CLIENT-001", "c1", requestedAmount = "12000.00", termMonths = 12), "stable-key")
        assertFalse(result.simulated); assertEquals(1, tokens.refreshes)
        val first = server.takeRequest(); val retry = server.takeRequest()
        assertEquals("/api/v1/credit-assessments", first.path); assertEquals("POST", first.method)
        assertEquals("stable-key", first.getHeader("Idempotency-Key")); assertEquals("stable-key", retry.getHeader("Idempotency-Key"))
        assertEquals(first.body.readUtf8(), retry.body.readUtf8()); assertEquals("Bearer renewed", retry.getHeader("Authorization"))
    }
    @Test fun repeatedUnauthorizedExpiresSessionWithoutLoop() = runTest {
        respond("{}", 401); respond("{}", 401)
        try { api.usage(); fail("Se esperaba un error de sesión") } catch (failure: ApiException) { assertEquals(401, failure.status) }
        assertEquals(2, server.requestCount); assertTrue(tokens.rejected)
    }
    @Test fun errorsKeepBusinessCodeAndCorrelationWithoutLocalFallback() = runTest {
        respond("""{"title":"Cuota agotada","status":429,"detail":"Límite mensual","code":"QUOTA_EXCEEDED","correlationId":"request-1"}""", 429)
        try { api.usage(); fail("Se esperaba un error de la API") } catch (failure: ApiException) { assertEquals("QUOTA_EXCEEDED", failure.code); assertEquals("request-1", failure.correlationId) }
        respond("<html>Error</html>")
        try { api.tenant(); fail("Una respuesta inválida no debe inventar una institución") } catch (_: ApiException) { }
    }
    @Test fun detailsReviewResolutionAndAnalyticsUseServerContracts() = runTest {
        respond(APPLICANT); api.applicant("a1"); assertEquals("/api/v1/solicitantes/a1", server.takeRequest().path)
        respond("""{"items":[],"total":0}"""); api.assessments("a1", since = "2026-10-01T00:00:00Z")
        server.takeRequest().let { assertEquals("a1", it.requestUrl!!.queryParameter("solicitanteId")); assertEquals("2026-10-01T00:00:00Z", it.requestUrl!!.queryParameter("desde")) }
        respond("""{"simulado":false}"""); api.assessment("e1"); assertEquals("/api/v1/credit-assessments/e1", server.takeRequest().path)
        respond("""{"items":[],"total":0,"productos":[]}"""); api.reviewCases(2, 20); assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("pagina"))
        respond("""{"evaluacionId":"e1","decision":"RECHAZADO","justificacion":"Motivo verificado","resueltoPor":"analyst","resueltoEn":"2026-10-01T00:00:00Z"}""")
        api.resolve("e1", NewResolution("RECHAZADO", justification = "Motivo verificado"))
        server.takeRequest().let { assertEquals("/api/v1/revision-manual/e1/resolucion", it.path); assertFalse(it.body.readUtf8().contains("productoId")) }
        respond("""{"desde":"2026-10-01","hasta":"2026-10-14","plan":"FREE","moneda":"BOB","analiticaAvanzada":false,"evaluaciones":0,"integracionesActivas":1,"porcentajeConRecomendacion":0,"necesidades":[]}""")
        assertNull(api.analytics("2026-10-01", "2026-10-14").averageScore)
        server.takeRequest().let { assertEquals("2026-10-01", it.requestUrl!!.queryParameter("desde")); assertEquals("2026-10-14", it.requestUrl!!.queryParameter("hasta")) }
    }
    private class TestTokens : AccessTokens {
        var refreshes = 0; var rejected = false
        override suspend fun accessToken(rejectedToken: String?): String { if (rejectedToken != null) refreshes++; return if (refreshes == 0) "original" else "renewed" }
        override suspend fun reject(token: String) { rejected = true }
    }
}
