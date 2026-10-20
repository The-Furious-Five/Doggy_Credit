package com.doggycredit.app

import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
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
    private fun launch(dark: Boolean = false, fontScale: Float = 1f, privacy: State<Boolean>? = null,
        api: DoggyCreditApi = FixtureApi(), lock: MutableState<Boolean>? = null, session: SessionController = FixtureSession()): AppViewModel {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, session, api)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DoggyCreditTheme(darkTheme = dark) { DoggyCreditApp(vm, {}, lock?.value ?: false, lock != null,
                    { lock?.value = it }, {}, privacyProtected = privacy?.value ?: false) }
            }
        }
        compose.waitUntil(10_000) { !vm.restoring && vm.home.value != null && (vm.reviews.value != null || vm.reviews.error != null) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        return vm
    }
    @Test fun clientsOpenFinancialProfileAndAssessment() {
        launch()
        navigate("Clientes")
        compose.onNodeWithText("CLIENT-001").performClick()
        compose.onNodeWithText("Perfil Financiero 360").assertExists()
        profileNode("Historial de evaluaciones")
        profileNode("Preaprobado").performClick()
        compose.onNodeWithContentDescription("Score 710 de 1000, banda B").assertExists()
        capture("assessment")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Preaprobado").assertIsDisplayed()
    }
    @Test fun evaluationCompletesFourNativeSteps() {
        val vm = launch()
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
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithContentDescription("Nueva evaluación").performClick()
        compose.onNode(hasSetTextAction() and hasText("CI o NIT")).assertExists()
        compose.runOnIdle { org.junit.Assert.assertEquals("", vm.draft.document); org.junit.Assert.assertNull(vm.draft.result) }
    }
    @Test fun reviewRequiresJustificationAndRecordsResolution() {
        launch()
        navigate("Revisión")
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
        navigate("Analítica")
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
        compose.onNodeWithText("Resolver caso").assertIsDisplayed()
        val action = compose.onNodeWithText("Resolver caso").fetchSemanticsNode().boundsInRoot
        val fab = compose.onNodeWithContentDescription("Nueva evaluación").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("La primera resolución debe verse sin desplazarse ni quedar bajo el FAB", action.bottom <= fab.top)
        capture("home")
        compose.runOnIdle { vm.draft.document = "CLIENT-001" }
        navigate("Clientes")
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
        navigate("Clientes")
        compose.onNodeWithText("CLIENT-001").assertExists()
        capture("clients-dark-large-text")
    }
    @Test fun privacyHidesEvaluationWithoutLosingDraftOrNavigation() {
        val privacy = mutableStateOf(false)
        val vm = launch(privacy = privacy)
        navigate("Clientes")
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
    @Test fun newEvaluationFromAnotherProfileRequiresAnExplicitChoice() {
        val vm = launch()
        compose.runOnIdle { vm.draft.document = "OLD-001"; vm.draft.income = "3500" }
        navigate("Clientes")
        compose.onNodeWithText("CLIENT-001").performClick()
        compose.onNodeWithText("Nueva evaluación").performClick()
        compose.onNodeWithText("Tienes una evaluación en curso").assertExists()
        compose.onNodeWithText("El borrador corresponde a OLD-001.").assertExists()
        compose.onNodeWithText("Quieres iniciar una evaluación para CLIENT-001.").assertExists()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("OLD-001", vm.draft.document) }
        compose.onNodeWithText("Nueva evaluación").performClick()
        compose.onNodeWithText("Retomar evaluación").performClick()
        compose.onNode(hasSetTextAction() and hasText("OLD-001")).assertExists()
        compose.onNodeWithContentDescription("Cerrar y conservar borrador").performClick()
        compose.onNodeWithText("Nueva evaluación").performClick()
        compose.onNodeWithText("Descartar y crear nueva").performClick()
        compose.onNode(hasSetTextAction() and hasText("CLIENT-001")).assertExists()
        compose.runOnIdle { org.junit.Assert.assertEquals("", vm.draft.income) }
    }
    @Test fun draftConflictRespectsMaskingPrivacyAndBusyState() {
        val privacy = mutableStateOf(false)
        val api = object : DoggyCreditApi by FixtureApi() { override suspend fun preferences() = Preferences(true) }
        val vm = launch(fontScale = 1.4f, dark = true, privacy = privacy, api = api, session = FixtureSession(listOf("ANALYST")))
        compose.runOnIdle {
            vm.draft.document = "OLD-001"
            vm.draft.busy = true
            vm.startEvaluation("CLIENT-001")
        }
        compose.onNodeWithText("El borrador corresponde a ${Display.document("OLD-001", true)}.").assertExists()
        compose.onNodeWithText("El borrador corresponde a OLD-001.").assertDoesNotExist()
        compose.onNodeWithText("Descartar y crear nueva").assertIsNotEnabled()
        compose.runOnIdle { privacy.value = true }
        compose.onNodeWithText("Tienes una evaluación en curso").assertDoesNotExist()
        compose.runOnIdle { privacy.value = false; vm.draft.busy = false }
        compose.onNodeWithText("Descartar y crear nueva").assertIsEnabled()
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("OLD-001", vm.draft.document) }
    }
    @Test fun validationIdentifiesAndFocusesTheInvalidField() {
        val vm = launch()
        compose.onNodeWithContentDescription("Nueva evaluación").performClick()
        field("CI o NIT", "CLIENT-001")
        field("Actividad económica", "Comercio")
        field("Ingreso mensual declarado (Bs)", "5000,50")
        field("Plazo (meses)", "361")
        compose.onNodeWithText("Capital de trabajo").performScrollTo().performClick()
        compose.onAllNodesWithText("Continuar").onLast().performClick()
        compose.onNodeWithText("El plazo debe estar entre 1 y 360 meses.").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Plazo (meses)")).assertIsFocused()
        compose.runOnIdle { org.junit.Assert.assertEquals(0, vm.draft.step) }
        field("Plazo (meses)", "12")
        compose.onNodeWithText("El plazo debe estar entre 1 y 360 meses.").assertDoesNotExist()
        compose.onAllNodesWithText("Continuar").onLast().performClick()
        compose.onNodeWithText("Autorización del cliente").assertExists()
    }
    @Test fun filteredInboxDoesNotClaimAnEmptyQueueBeforeAllPagesAreLoaded() {
        val first = ReviewCase("r1", "a1", "PENDING-1", "2026-10-01T00:00:00Z", 550, "C", "REVISION_MANUAL", false, emptyList())
        val second = first.copy(assessmentId = "r2", document = "PARTIAL-2", partial = true)
        val api = object : DoggyCreditApi by FixtureApi() {
            override suspend fun reviewCases(page: Int, size: Int) = ReviewPage(listOf(if (page == 1) first else second), 2, emptyList())
        }
        launch(api = api)
        navigate("Revisión")
        compose.onNodeWithText("Parciales").performScrollTo().performClick()
        compose.onNodeWithText("Sin coincidencias en los casos cargados").assertExists()
        compose.onNodeWithText("Sin casos pendientes").assertDoesNotExist()
        capture("reviews-partial-filter")
        compose.onNodeWithText("Cargar más casos").performScrollTo().performClick()
        compose.onNodeWithText("PARTIAL-2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Sin coincidencias en los casos cargados").assertDoesNotExist()
    }
    @Test fun accountLockCanBeToggledByItsAccessibleLabel() {
        val lock = mutableStateOf(false)
        launch(lock = lock)
        compose.onNodeWithContentDescription("Cuenta de Analista de pruebas").performClick()
        compose.onNode(hasText("Bloquear la aplicación") and isToggleable()).performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { org.junit.Assert.assertTrue(lock.value) }
    }
    @Test fun homeLayoutKeepsWorkReachableWithLargeTextAndLongContent() {
        val base = FixtureApi()
        val api = object : DoggyCreditApi by base {
            override suspend fun tenant() = base.tenant().copy(name = "Cooperativa de ahorro y crédito para emprendedores y pequeñas empresas de Bolivia")
            override suspend fun usage() = TenantUsage(Int.MAX_VALUE, Int.MAX_VALUE)
            override suspend fun applicants(page: Int, size: Int, segment: String?, document: String?) =
                base.applicants(page, size, segment, document).copy(total = Int.MAX_VALUE)
            override suspend fun reviewCases(page: Int, size: Int): ReviewPage {
                val response = base.reviewCases(page, size)
                return response.copy(items = response.items.map { it.copy(score = 1000,
                    purpose = "Inversión productiva para ampliar la actividad económica y renovar equipamiento",
                    reasons = listOf("La información consolidada requiere verificar la capacidad de pago y contrastar las fuentes autorizadas durante la visita.")) })
            }
        }
        val vm = launch(dark = true, fontScale = 2f, api = api)
        compose.runOnIdle { vm.draft.document = "CLIENT-001" }
        compose.onNodeWithText("Continuar").performScrollTo().assertIsDisplayed()
        assertAboveLargeTextActions(compose.onNodeWithText("Continuar"))
        capture("home-draft-large-text")
        compose.onNodeWithContentDescription("Score 1000 de 1000, banda C").performScrollTo().assertIsDisplayed()
        assertAboveLargeTextActions(compose.onNodeWithContentDescription("Score 1000 de 1000, banda C"))
        capture("home-large-text-score")
        compose.onNodeWithText("Resolver caso").performScrollTo().assertIsDisplayed()
        assertAboveLargeTextActions(compose.onNodeWithText("Resolver caso"))
        capture("home-large-text-review")
        compose.onNodeWithText("Ver bandeja").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Clientes en cartera").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Actividad de este mes").performScrollTo().assertIsDisplayed()
        assertAboveLargeTextActions(compose.onNodeWithText("Actividad de este mes"))
        capture("home-large-text-summary")
        compose.onNodeWithText("Retomar").performClick()
        compose.onNode(hasSetTextAction() and hasText("CLIENT-001")).assertExists()
    }
    private fun assertAboveLargeTextActions(node: SemanticsNodeInteraction) {
        val content = node.fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("large-text-actions").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("El contenido debe quedar completamente por encima de las acciones fijas", content.bottom <= actions.top)
    }
    @Test fun homeEmptyAndErrorStatesKeepRecoveryReachable() {
        var available = false
        val base = FixtureApi()
        val api = object : DoggyCreditApi by base {
            override suspend fun reviewCases(page: Int, size: Int): ReviewPage {
                if (!available) throw java.io.IOException("Revisa tu conexión y vuelve a intentarlo.")
                return ReviewPage(emptyList(), 0, emptyList())
            }
            override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?) = Page<AssessmentSummary>(emptyList(), 0)
        }
        val vm = launch(api = api)
        compose.onNodeWithText("Reintentar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Todo al día").assertDoesNotExist()
        capture("home-error")
        available = true
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitUntil(10_000) { vm.reviews.value != null }
        compose.onNodeWithText("Todo al día").assertIsDisplayed()
        capture("home-empty")
        compose.onNodeWithText("Nueva evaluación").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("CI o NIT")).assertExists()
    }
    @Test fun polishedPathKeepsProfileResultReviewAndAnalyticsLegible() {
        val base = FixtureApi()
        val api = object : DoggyCreditApi by base {
            override suspend fun assessment(id: String) = base.assessment(id).copy(score = 1000)
            override suspend fun analytics(from: String, to: String) = base.analytics(from, to).copy(assessments = Int.MAX_VALUE)
        }
        launch(dark = true, fontScale = 2f, api = api)
        compose.onNodeWithContentDescription("Cuenta de Analista de pruebas").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        navigate("Clientes")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("CLIENT-001"))
        compose.onNodeWithText("CLIENT-001").performClick()
        profileNode("Capacidad de pago").assertIsDisplayed()
        capture("polish-profile-large-text")
        profileNode("Preaprobado").performClick()
        compose.onNodeWithContentDescription("Score 1000 de 1000, banda B").performScrollTo().assertIsDisplayed()
        capture("polish-result-large-text")
        compose.onNodeWithText("Resultado de la evaluación").performScrollTo().assertIsDisplayed()
        navigate("Revisión")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Resolver caso"))
        compose.onNodeWithText("Resolver caso").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Score 550 de 1000, banda C").performScrollTo().assertIsDisplayed()
        capture("polish-resolution-large-text")
        compose.onNodeWithText("Ver evaluación").performScrollTo().assertIsDisplayed()
        navigate("Analítica")
        val count = compose.onNodeWithText(Display.number(Int.MAX_VALUE)).performScrollTo().assertIsDisplayed()
        val textLayouts = mutableListOf<TextLayoutResult>()
        count.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
        capture("polish-analytics-metrics-large-text")

        compose.onNodeWithContentDescription("Distribución de 12 necesidades registradas.", substring = true).performScrollTo().assertIsDisplayed()
        capture("polish-analytics-chart-large-text")
        // Los anchos intrínsecos pueden diferir por una fracción de píxel al redondear.
        org.junit.Assert.assertTrue("El contador debe ser legible sin recortar sus cifras: " + textLayouts.joinToString {
            "${it.size}, líneas=${it.lineCount}, borde=${it.getLineRight(it.lineCount - 1)}"
        }, textLayouts.isNotEmpty() && textLayouts.none { layout ->
            layout.didOverflowHeight || (0 until layout.lineCount).any { line ->
                layout.getLineRight(line) > layout.size.width + 1f || layout.getLineLeft(line) < -1f
            }
        })
    }
    private fun navigate(label: String) {
        val menu = compose.onAllNodesWithContentDescription("Abrir navegación")
        if (menu.fetchSemanticsNodes().isNotEmpty()) {
            menu.onFirst().performClick()
            compose.onNode(hasText(label) and hasClickAction()).performScrollTo().performClick()
        } else compose.onNode(hasText(label) and hasClickAction()).performClick()
    }
    private fun profileNode(text: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("page-scroll").performScrollToNode(hasText(text))
        return compose.onNodeWithText(text)
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
internal class FixtureSession(roles: List<String> = listOf("TENANT_ADMIN")) : SessionController {
    override val user = MutableStateFlow<UserSession?>(UserSession("Analista de pruebas", "test", "t1", roles))
    override fun authorizationIntent() = Intent()
    override suspend fun complete(data: Intent): SessionTokens? = null
    override suspend fun restore() {}
    override suspend fun accept(value: SessionTokens) {}
    override suspend fun signOut() { user.value = null }
    override suspend fun accessToken(rejectedToken: String?) = "test-only"
    override suspend fun reject(token: String) { user.value = null }
}
internal class FixtureApi : DoggyCreditApi {
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
