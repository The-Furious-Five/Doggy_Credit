package com.doggycredit.app

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.doggycredit.app.auth.SecureSessionStore
import com.doggycredit.app.auth.SessionTokens
import com.doggycredit.app.model.Assessment
import com.doggycredit.app.model.EvaluationDraft
import com.doggycredit.app.ui.components.AssessmentContent
import com.doggycredit.app.ui.screens.ConsentStep
import com.doggycredit.app.ui.screens.WelcomeScreen
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

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
    @Test fun secureStorageRoundTripsAndClearsCredentials() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage = SecureSessionStore(context)
        try {
            val tokens = SessionTokens("test-access", "test-refresh", null, 123456789)
            storage.write(tokens)
            assertEquals(tokens, storage.read())
            val raw = context.noBackupFilesDir.resolve("session.enc").readBytes().decodeToString()
            assertFalse(raw.contains("test-access")); assertFalse(raw.contains("test-refresh"))
            storage.clear(); assertNull(storage.read())
        } finally { storage.clear() }
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
}
