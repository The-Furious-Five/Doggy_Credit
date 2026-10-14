package com.doggycredit.app

import com.doggycredit.app.auth.userFromToken
import com.doggycredit.app.data.Server
import com.doggycredit.app.model.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ContractTests {
    @Test fun profileContractSupportsMicrosecondsAndMissingFields() {
        val applicant = ApiJson.decodeFromString<Applicant>(APPLICANT)
        assertEquals("CLIENT-001", applicant.document)
        assertEquals("BOB", applicant.currency)
        assertNull(applicant.profile)
        assertTrue(Display.date(applicant.createdAt).contains("2026"))
    }
    @Test fun profileAcceptsOptionalIncomeComparisonFieldsFromMergedApi() {
        val profile = ApiJson.decodeFromString<FinancialProfile>("""
            {"id":"profile-1","hash":"ab12","creadoEn":"2026-10-01T14:03:22Z",
             "ingresoMensual":{"amount":"9500.00","currency":"BOB"},"origenIngreso":"BANCO",
             "ingresoDeclarado":{"amount":"5000.00","currency":"BOB"},"discrepanciaIngreso":true,
             "fuentes":[{"fuente":"BANCO","estado":"COMPLETA"}]}
        """.trimIndent())
        assertEquals("9500.00", profile.monthlyIncome.amount)
        assertEquals("BANCO", profile.incomeOrigin)
        assertEquals("COMPLETA", profile.sources.single().state)
        assertNull(profile.debt)
    }
    @Test fun simulatedFlagIsNeverHidden() {
        assertTrue(ApiJson.decodeFromString<Assessment>("{}").simulated)
        assertFalse(ApiJson.decodeFromString<Assessment>("""{"score":710,"simulado":false}""").simulated)
    }
    @Test fun missingScoreIsUnknownInsteadOfZero() {
        val assessment = ApiJson.decodeFromString<Assessment>("{}")
        assertNull(assessment.score); assertNull(assessment.band); assertTrue(assessment.reasons.isEmpty())
    }
    @Test fun requestUsesSpanishContractKeysAndOmitsNullProduct() {
        val value = ApiJson.encodeToString(NewResolution("RECHAZADO", justification = "Justificación de la decisión"))
        assertTrue(value.contains("justificacion")); assertFalse(value.contains("productoId"))
        val body = ApiJson.encodeToString(NewAssessment("CI-1", "consent", requestedAmount = "12000.00", termMonths = 12))
        assertTrue(body.contains("titularDocumento")); assertTrue(body.contains("montoSolicitado")); assertTrue(body.contains("plazoMeses"))
    }
    @Test fun requiredDefaultsAreEncodedForRealBackend() {
        val applicant = ApiJson.encodeToString(NewApplicant("CLIENT-001", "Comercio", 24, "5000.00", purpose = "Capital de trabajo", segment = "PERSONA"))
        assertTrue(applicant.contains("\"moneda\":\"BOB\""))
        val consent = ApiJson.encodeToString(NewConsent("CLIENT-001", listOf("BURO")))
        assertTrue(consent.contains("\"finalidad\":\"Evaluación de crédito\""))
        assertTrue(consent.contains("\"vigenciaDias\":90"))
    }
    @Test fun amountsUseExactDecimalsAndBolivianInput() {
        assertEquals("12500.50", Display.apiAmount("12500,50"))
        assertEquals("1234.57", Display.apiAmount("1234.567"))
        assertNull(Display.apiAmount("")); assertNull(Display.apiAmount("1,234.50"))
    }
    @Test fun unknownMoneyIsNotDisplayedAsZero() { assertEquals("—", Display.money(null as String?)); assertEquals("—", Display.percent(null)) }
    @Test fun privacyMasksUntilPreferencesAreAvailable() {
        val analyst = UserSession("A", "a", "id", listOf("ANALYST"))
        val admin = analyst.copy(roles = listOf("TENANT_ADMIN"))
        assertTrue(analyst.shouldMask(null)); assertTrue(admin.shouldMask(null))
        assertTrue(analyst.shouldMask(Preferences(true))); assertFalse(admin.shouldMask(Preferences(true)))
        assertEquals("CI •••• 5667", Display.document("CI-4455667", true))
        assertEquals("••••••", Display.amount("Bs 12000", true))
    }
    @Test fun quotaWarningsUseServerLimit() {
        assertFalse(TenantUsage(399, 500).warning); assertTrue(TenantUsage(400, 500).warning)
        assertTrue(TenantUsage(500, 500).exhausted); assertNull(TenantUsage(10000, 0).fraction)
    }
    @Test fun accountMustHaveCorrectIssuerAudienceAndTenant() {
        val good = token("""{"iss":"${Server.ISSUER}","aud":["doggycredit-api"],"tenant_id":"9c7e7c1a-0000-4000-8000-000000000001","preferred_username":"analyst","realm_access":{"roles":["ANALYST","offline_access"]}}""")
        val user = userFromToken(good)
        assertEquals("analyst", user.username); assertEquals(listOf("ANALYST"), user.roles); assertTrue(user.canResolve)
        assertThrows(IllegalArgumentException::class.java) { userFromToken(good.replace(good.split('.')[1], token("""{"iss":"https://other.example"}""").split('.')[1])) }
    }
    @Test fun malformedTokenCannotCreateSession() { assertThrows(Exception::class.java) { userFromToken("not-a-token") } }
    private fun token(claims: String) = "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(claims.encodeToByteArray())}.signature"
}
internal const val APPLICANT = """{"id":"a1","documento":"CLIENT-001","actividad":"Comercio","antiguedadActividadMeses":24,"ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital de trabajo","segmento":"PERSONA","creadoEn":"2026-10-01T14:03:22.123456Z"}"""
internal const val CONSENT = """{"id":"c1","titularDocumento":"CLIENT-001","fuentes":["BURO"],"finalidad":"Evaluación de crédito","estado":"ACTIVO"}"""
