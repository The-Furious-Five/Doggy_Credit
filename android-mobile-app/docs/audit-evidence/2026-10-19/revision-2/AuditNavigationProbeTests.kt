package com.doggycredit.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.auth.*
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.DoggyCreditApp
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Diagnóstico temporal con sesión y API de prueba, sin red. */
class AuditNavigationProbeTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun protectedBackMustWinOverRealFinancialNavigation() {
        val protected = mutableStateOf(false)
        val leaves = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, AuditFixtureSession(), AuditFixtureApi())
        compose.setContent {
            DoggyCreditTheme {
                ProtectedSession(protected.value, { leaves.incrementAndGet() }, { LockScreen({}, null, {}) }) {
                    DoggyCreditApp(vm, {}, true, true, {}, {}, privacyProtected = protected.value)
                }
            }
        }
        compose.waitUntil(10_000) { !vm.restoring && vm.home.value != null && vm.reviews.value != null }
        compose.onNodeWithContentDescription("Cuenta de Analista de pruebas").performClick()
        compose.onNodeWithText("Tu institución").assertExists()
        compose.runOnIdle { protected.value = true }
        compose.onNodeWithText("Tu información está protegida").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        val observedLeaves = leaves.get()
        compose.onNodeWithText("Tu información está protegida").assertIsDisplayed()
        compose.runOnIdle { protected.value = false }
        compose.waitForIdle()
        val accountRetained = compose.onAllNodesWithText("Tu institución").fetchSemanticsNodes().isNotEmpty()
        println("REAUDIT protected_back leaves=$observedLeaves account_retained=$accountRetained expected_leaves=1 expected_account_retained=true")
        assertEquals("La salida protegida debe recibir Atrás incluso con Cuenta abierta", 1, observedLeaves)
        assertTrue("Atrás en la portada no debe navegar por el contenido oculto", accountRetained)
    }
}

private class AuditFixtureSession(roles: List<String> = listOf("TENANT_ADMIN")) : SessionController {
    override val user = MutableStateFlow<UserSession?>(UserSession("Analista de pruebas", "test", "t1", roles))
    override fun authorizationIntent() = Intent()
    override suspend fun complete(data: Intent): SessionTokens? = null
    override suspend fun restore() {}
    override suspend fun accept(value: SessionTokens) {}
    override suspend fun signOut() { user.value = null }
    override suspend fun accessToken(rejectedToken: String?) = "test-only"
    override suspend fun reject(token: String) { user.value = null }
}
private class AuditFixtureApi : DoggyCreditApi {
    private val applicant = ApiJson.decodeFromString<Applicant>("""{"id":"a1","documento":"CLIENT-001","actividad":"Comercio","antiguedadActividadMeses":24,"ingresosMensuales":"5000.00","moneda":"BOB","destinoCredito":"Capital de trabajo","segmento":"PERSONA","creadoEn":"2026-10-01T14:03:22Z","perfil":{"id":"profile-1","hash":"test-hash","creadoEn":"2026-10-01T14:03:22Z","ingresoMensual":{"amount":"5000.00","currency":"BOB"},"origenIngreso":"DECLARADO","fuentes":[{"fuente":"BURO","estado":"COMPLETA"}]}}""")
    private val summary = AssessmentSummary("e1", "a1", "PREAPROBADO", "PREAPROBADO", 710, "B", "12000.00", 12, "2026-10-01T14:03:22Z")
    private val consent = Consent("c1", "CLIENT-001", listOf("BURO"), "Evaluación de crédito", status = "ACTIVO")
    private var pending = true
    override suspend fun tenant() = Tenant("t1", "Institución de pruebas UI", "1", "IMF", "PRO", "BO", "BOB", true)
    override suspend fun usage() = TenantUsage(12, 500)
    override suspend fun preferences() = Preferences(false)
    override suspend fun applicants(page: Int, size: Int, segment: String?, document: String?) = Page(listOf(applicant), 1)
    override suspend fun createApplicant(body: NewApplicant) = applicant
    override suspend fun applicant(id: String) = applicant
    override suspend fun consents(applicantId: String) = listOf(consent)
    override suspend fun createConsent(body: NewConsent) = consent
    override suspend fun revokeConsent(id: String) = consent.copy(status = "REVOCADO")
    override suspend fun integrations() = listOf(Integration("BURO", "CONECTADO"), Integration("BANCO", "DISPONIBLE"))
    override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?) = Page(listOf(summary), 1)
    override suspend fun createAssessment(body: NewAssessment, key: String) = assessment("e1")
    override suspend fun assessment(id: String) = Assessment("e1", "PREAPROBADO", "PREAPROBADO", 710, "B", listOf("SIN_MORA_12M"), "scorecard-v1", false, "test-correlation")
    override suspend fun reviewCases(page: Int, size: Int) = ReviewPage(
        if (pending) listOf(ReviewCase("e1", "a1", "CLIENT-001", "2026-10-01T14:03:22Z", 550, "C", "REVISION_MANUAL", false, listOf("SIN_MORA_12M"), "12000.00", 12)) else emptyList(),
        if (pending) 1 else 0, listOf(Product("p1", "MICROCREDITO", "BOB", "1000.00", "50000.00", 6, 36, "10.00", "C", true)))
    override suspend fun resolve(id: String, body: NewResolution) = Resolution(id, body.decision, body.productId, body.justification, "test", "2026-10-14T00:00:00Z").also { pending = false }
    override suspend fun analytics(from: String, to: String) = AnalyticsSummary(from, to, "PRO", "BOB", true, 12, 710.0, 1, 75.0, listOf(Need("CAPITAL_TRABAJO", 12, 100.0)),
        AdvancedAnalytics(75.0, 400.0, "120000.00", listOf(ProductBreakdown("MICROCREDITO", 12, 100.0, "120000.00")), listOf(ModelMonitor("scorecard-v1", .02, "ESTABLE", from, to, 1200, 3000))))
}
