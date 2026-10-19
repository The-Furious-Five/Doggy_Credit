package com.doggycredit.app

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.auth.*
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.DoggyCreditApp
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Fixtures compiladas solo en el APK de instrumentación, sin modificar datos del VPS. */
class AuthenticatedFlowTests {
    @get:Rule val compose = createComposeRule()
    private fun launch(dark: Boolean = false, fontScale: Float = 1f, privacy: State<Boolean>? = null): AppViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, FixtureSession(), FixtureApi())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DoggyCreditTheme(darkTheme = dark) { DoggyCreditApp(vm, {}, false, false, {}, {}, privacyProtected = privacy?.value ?: false) }
            }
        }
        compose.waitUntil(10_000) { !vm.restoring && vm.home.value != null && vm.reviews.value != null }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        return vm
    }
    @Test fun clientsOpenFinancialProfileAndAssessment() {
        launch()
        compose.onNodeWithText("Clientes").performClick()
        compose.onNodeWithText("CLIENT-001").performClick()
        compose.onNodeWithText("Perfil Financiero 360").assertExists()
        compose.onNodeWithText("Historial de evaluaciones").performScrollTo()
        compose.onNodeWithText("Preaprobado").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Score 710 de 1000, banda B").assertExists()
        capture("assessment")
    }
    @Test fun evaluationCompletesFourNativeSteps() {
        launch()
        compose.onNodeWithContentDescription("Nueva evaluación").performClick()
        field("CI o NIT", "CLIENT-001")
        field("Actividad económica", "Comercio")
        field("Ingreso mensual declarado (Bs)", "5000")
        compose.onNodeWithText("Capital de trabajo").performScrollTo().performClick()
        compose.onAllNodesWithText("Continuar").onLast().performClick()
        compose.onNodeWithText("Buró de crédito · Obligatorio").performScrollTo().performClick()
        compose.onAllNodesWithText("Continuar").onLast().performClick()
        compose.onNodeWithText("Evaluar cliente").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Score 710 de 1000, banda B").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Sin mora en los últimos 12 meses").onLast().assertExists()
        capture("evaluation-result")
    }
    @Test fun reviewRequiresJustificationAndRecordsResolution() {
        launch()
        compose.onNodeWithText("Revisión").performClick()
        compose.onNodeWithText("Resolver caso").performClick()
        compose.onNodeWithText("Rechazado").performScrollTo().performClick()
        field("Justificación obligatoria", "Capacidad de pago verificada durante la visita")
        compose.onNodeWithText("Rechazar caso").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Caso resuelto").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Caso resuelto").assertExists()
        capture("review-resolution")
    }
    @Test fun analyticsAndAccountExposePlanAndPrivacy() {
        launch()
        compose.onNodeWithText("Analítica").performClick()
        compose.onNodeWithText("Necesidades detectadas").performScrollTo().assertExists()
        capture("analytics")
        compose.onNodeWithContentDescription("Cuenta de Analista de pruebas").performClick()
        compose.onNodeWithText("Tu institución").assertExists()
        compose.onNodeWithText("Cerrar sesión").performScrollTo().performClick()
        compose.onAllNodesWithText("Cerrar sesión").filter(hasClickAction()).onLast().performClick()
        compose.onNodeWithText("Iniciar sesión con tu institución").assertExists()
    }
    @Test fun rootUsesBrandAndKeepsDraftAcrossNavigation() {
        val vm = launch()
        capture("home")
        compose.runOnIdle { vm.draft.document = "CLIENT-001" }
        compose.onNodeWithText("Clientes").performClick()
        compose.onNodeWithText("Retomar").assertExists().performClick()
        compose.onNode(hasSetTextAction() and hasText("CLIENT-001")).assertExists()
    }
    @Test fun homeContentScrollsBehindFabAndLastCardIsReachable() {
        launch()
        val viewport = compose.onNodeWithTag("page-viewport").fetchSemanticsNode().boundsInRoot
        val fab = compose.onNodeWithContentDescription("Nueva evaluación").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("El contenido debe llegar hasta debajo del FAB, sin una franja vacía", viewport.bottom > fab.bottom)
        compose.onNodeWithTag("page-scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        compose.waitForIdle()
        compose.onNodeWithText("Evaluaciones recientes").assertIsDisplayed()
        compose.onAllNodes(hasText("CLIENT-001") and hasClickAction()).onLast().assertIsDisplayed()
        capture("home-scrolled")
    }
    @Test fun darkThemeSupportsLargeSystemText() {
        launch(dark = true, fontScale = 1.4f)
        compose.onNodeWithContentDescription("Nueva evaluación").assertExists().assertHasClickAction()
        compose.onNodeWithText("Clientes").performClick()
        compose.onNodeWithText("CLIENT-001").assertExists()
        capture("clients-dark-large-text")
    }
    @Test fun privacyHidesEvaluationWithoutLosingDraftOrNavigation() {
        val privacy = mutableStateOf(false)
        val vm = launch(privacy = privacy)
        compose.onNodeWithText("Clientes").performClick()
        compose.onNodeWithContentDescription("Nueva evaluación").performClick()
        field("CI o NIT", "CLIENT-001")
        compose.runOnIdle { privacy.value = true }
        compose.onNodeWithText("CI o NIT").assertDoesNotExist()
        compose.runOnIdle { privacy.value = false }
        compose.onNode(hasSetTextAction() and hasText("CLIENT-001")).assertExists()
        compose.onNodeWithContentDescription("Cerrar y conservar borrador").performClick()
        compose.onNode(hasText("Clientes") and isSelected()).assertExists()
        compose.runOnIdle { org.junit.Assert.assertEquals("CLIENT-001", vm.draft.document) }
    }
    private fun field(label: String, text: String) { compose.onNode(hasSetTextAction() and hasText(label)).performScrollTo().performTextReplacement(text) }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        val directory = File(base ?: context.getExternalFilesDir(null)!!.path, "test-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
private class FixtureSession : SessionController {
    override val user = MutableStateFlow<UserSession?>(UserSession("Analista de pruebas", "test", "t1", listOf("TENANT_ADMIN")))
    override fun authorizationIntent() = Intent()
    override suspend fun complete(data: Intent): SessionTokens? = null
    override suspend fun restore() {}
    override suspend fun accept(value: SessionTokens) {}
    override suspend fun signOut() { user.value = null }
    override suspend fun accessToken(rejectedToken: String?) = "test-only"
    override suspend fun reject(token: String) { user.value = null }
}
private class FixtureApi : DoggyCreditApi {
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
