package com.doggycredit.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.doggycredit.app.auth.SecureSessionStore
import com.doggycredit.app.auth.SessionTokens
import com.doggycredit.app.model.Assessment
import com.doggycredit.app.model.EvaluationDraft
import com.doggycredit.app.ui.components.AssessmentContent
import com.doggycredit.app.ui.components.PageColumn
import com.doggycredit.app.ui.components.ValidatedTextField
import com.doggycredit.app.ui.screens.ConsentStep
import com.doggycredit.app.ui.screens.WelcomeScreen
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class NativeUiTests {
    @get:Rule val compose = createComposeRule()
    @Test fun welcomeOnlyProvidesInstitutionSignIn() {
        var clicked = false
        compose.setContent { DoggyCreditTheme { WelcomeScreen(false, null, { clicked = true }, {}) } }
        compose.onNodeWithText("Iniciar sesión con tu institución").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(clicked) }
        compose.onNodeWithText("Explorar con datos de demostración").assertDoesNotExist()
        compose.onNodeWithContentDescription("DoggyCredit").assertExists()
    }
    @Test fun consentIsExplicitAndSourcesAreIndependent() {
        val draft = EvaluationDraft()
        compose.setContent { DoggyCreditTheme { Column { ConsentStep(draft) } } }
        compose.onNodeWithText("Buró de crédito · Obligatorio").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertTrue(draft.bureau); assertFalse(draft.bank); assertFalse(draft.wallet) }
        compose.onNodeWithText("Billetera móvil").performClick()
        compose.runOnIdle { assertTrue(draft.wallet); assertFalse(draft.bank) }
    }
    @Test fun serverSimulationFlagRemainsVisibleAndScoreIsAccessible() {
        compose.setContent { DoggyCreditTheme { AssessmentContent(Assessment(score = 710, band = "B", simulated = true, reasons = listOf("SIN_MORA_12M")), mask = true) } }
        compose.onNodeWithText("Datos simulados").assertExists()
        compose.onNodeWithContentDescription("Score 710 de 1000, banda B").assertExists()
        compose.onNodeWithText("Sin mora en los últimos 12 meses").assertExists()
    }
    @Test fun resultPanelSupportsLargeTextPartialAndMissingScoresInBothThemes() {
        val dark = mutableStateOf(false)
        val result = mutableStateOf(Assessment(status = "PARCIAL", score = 1000, band = "C", simulated = true))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DoggyCreditTheme(darkTheme = dark.value) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                        PageColumn { AssessmentContent(result.value, mask = true) }
                    }
                }
            }
        }
        compose.onNodeWithText("Datos simulados").assertIsDisplayed()
        val score = compose.onNodeWithContentDescription("Score 1000 de 1000, banda C").performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val decision = compose.onNodeWithText("Resultado parcial").fetchSemanticsNode().boundsInRoot
        assertTrue("Score y estado deben ocupar regiones separadas", score.right <= decision.left || score.bottom <= decision.top)
        capture("polish-result-partial-light-large-text")
        compose.runOnIdle { dark.value = true; result.value = result.value.copy(score = null, band = null) }
        compose.onNodeWithContentDescription("Score no disponible").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Datos simulados").assertExists()
        compose.onNodeWithText("Una fuente no respondió. Este resultado requiere revisión humana.").assertExists()
        compose.onNodeWithText("El servidor no devolvió razones para esta evaluación.").performScrollTo().assertIsDisplayed()
        capture("polish-result-missing-dark-large-text")
    }
    @Test fun secureStorageRoundTripsAndClearsCredentials() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = java.io.File(context.cacheDir, "secure-store-ui-test").apply { mkdirs() }
        val testContext = object : android.content.ContextWrapper(context) {
            override fun getNoBackupFilesDir() = directory
        }
        val storage = SecureSessionStore(testContext)
        try {
            val tokens = SessionTokens("test-access", "test-refresh", null, 123456789)
            storage.write(tokens)
            assertEquals(tokens, storage.read())
            val raw = directory.resolve("session.enc").readBytes().decodeToString()
            assertFalse(raw.contains("test-access")); assertFalse(raw.contains("test-refresh"))
            storage.clear(); assertNull(storage.read())
        } finally { storage.clear(); directory.deleteRecursively() }
    }
    @Test fun lockedScreenOffersUnlockAndSignOut() {
        var unlocked = false
        var signedOut = false
        compose.setContent { DoggyCreditTheme { LockScreen({ unlocked = true }, null, { signedOut = true }) } }
        compose.onNodeWithText("Desbloquear").performClick()
        compose.onNodeWithText("Cerrar sesión").performClick()
        compose.runOnIdle { assertTrue(unlocked); assertTrue(signedOut) }
    }
    @Test fun darkThemePreservesNativeSignInButton() {
        compose.setContent { DoggyCreditTheme(darkTheme = true) { WelcomeScreen(false, null, {}, {}) } }
        compose.onNodeWithText("Iniciar sesión con tu institución").assertIsDisplayed().assertHasClickAction()
    }
    @Test fun fieldValidationIsSpecificAndSupportsLargeDarkText() {
        val value = mutableStateOf("361")
        val submitted = mutableStateOf(false)
        val message = "El plazo debe estar entre 1 y 360 meses."
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                DoggyCreditTheme(darkTheme = true) {
                    androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    PageColumn {
                        ValidatedTextField(value.value, { value.value = it }, "Plazo (meses)",
                            validationError = message.takeUnless { value.value.toIntOrNull()?.let { it in 1..360 } == true },
                            hint = "De 1 a 360 meses.", submitted = submitted.value)
                    }
                    }
                }
            }
        }
        compose.onNodeWithText(message).assertDoesNotExist()
        compose.runOnIdle { submitted.value = true }
        compose.onNodeWithText(message).assertIsDisplayed()
        capture("field-validation-dark-large-text")
        compose.onNode(hasSetTextAction() and hasText("Plazo (meses)")).performTextReplacement("12")
        compose.onNodeWithText(message).assertDoesNotExist()
    }
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path
        val file = File(base, "test-screenshots/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
