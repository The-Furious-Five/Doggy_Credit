package com.doggycredit.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.ui.DoggyCreditApp
import com.doggycredit.app.ui.screens.WelcomeScreen
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AuditRemediationTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun protectedBackWinsOverRealNavigationAndKeepsEachRouteAndDraft() {
        val protected = mutableStateOf(false)
        val leaves = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, FixtureSession(), FixtureApi())
        compose.setContent { DoggyCreditTheme {
            ProtectedSession(protected.value, { leaves.incrementAndGet() }, { LockScreen({}, null, {}) }) {
                DoggyCreditApp(vm, {}, true, true, {}, {}, privacyProtected = protected.value)
            }
        } }
        compose.waitUntil(10_000) { !vm.restoring && vm.home.value != null && vm.reviews.value != null }
        compose.runOnIdle { vm.draft.document = "BORRADOR-CONSERVADO" }
        fun coverAndBack(expected: Int) {
            compose.runOnIdle { protected.value = true }
            compose.onNodeWithText("Tu información está protegida").assertIsDisplayed()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            assertEquals("La portada debe preceder al NavHost real", expected, leaves.get())
            compose.onNodeWithText("Tu información está protegida").assertIsDisplayed()
            compose.runOnIdle { protected.value = false; assertEquals("BORRADOR-CONSERVADO", vm.draft.document) }
        }
        coverAndBack(1)
        compose.onNodeWithContentDescription("Cuenta de Analista de pruebas").performClick()
        compose.onNodeWithText("Tu institución").assertExists()
        coverAndBack(2)
        compose.onNodeWithText("Tu institución").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasText("Clientes") and hasClickAction()).performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("CLIENT-001"))
        compose.onNodeWithText("CLIENT-001").performClick()
        coverAndBack(3)
        compose.onNodeWithTag("page-scroll").performScrollToNode(hasText("Perfil Financiero 360"))
        compose.onNodeWithText("Perfil Financiero 360").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("CLIENT-001").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cuenta de Analista de pruebas").assertIsDisplayed()
    }

    @Test fun enlargedNavigationKeepsAllLabelsSelectedStateAndDestinations() {
        val scale = mutableFloatStateOf(1.4f)
        val dark = mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, FixtureSession(), FixtureApi())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                DoggyCreditTheme(dark.value) { DoggyCreditApp(vm, {}, false, false, {}, {}) }
            }
        }
        compose.waitUntil(10_000) { !vm.restoring && vm.home.value != null }
        listOf(1.4f, 2f).forEach { font ->
            compose.runOnIdle { scale.floatValue = font; dark.value = font == 2f }
            for (label in listOf("Revisión", "Clientes", "Analítica", "Buscar", "Inicio")) {
                compose.onNodeWithContentDescription("Abrir navegación").performClick()
                val item = compose.onNode(hasText(label) and hasClickAction())
                item.performScrollTo().assertIsDisplayed().performClick()
                compose.onNodeWithContentDescription("Abrir navegación").assertIsDisplayed()
                compose.onNodeWithContentDescription("Abrir navegación").performClick()
                compose.onNode(hasText(label) and hasClickAction()).performScrollTo()
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
                if (label == "Inicio") capture("navigation-${if (font == 2f) "200-dark" else "140-light"}")
                compose.onNodeWithContentDescription("Cerrar navegación").performScrollTo().performClick()
            }
        }
    }

    @Test fun busyWelcomeRemainsLegibleAndPreventsRepeatedActionsInBothThemes() {
        val busy = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val calls = AtomicInteger()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DoggyCreditTheme(dark.value) { WelcomeScreen(busy.value, null, { calls.incrementAndGet() }, {}) }
            }
        }
        compose.onNodeWithText("Iniciar sesión con tu institución").performClick()
        assertEquals(1, calls.get())
        compose.runOnIdle { busy.value = true }
        listOf(false, true).forEach { appearance ->
            compose.runOnIdle { dark.value = appearance }
            compose.onNodeWithText("Conectando con tu institución…").assertIsDisplayed().assertIsNotEnabled()
            capture("welcome-busy-${if (appearance) "dark" else "light"}")
        }
        assertEquals(1, calls.get())
    }

    @Test fun protectedBackAlsoWinsWhileTheLargeTextDrawerIsOpen() {
        val protected = mutableStateOf(false)
        val leaves = AtomicInteger()
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, FixtureSession(), FixtureApi())
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DoggyCreditTheme { ProtectedSession(protected.value, { leaves.incrementAndGet() }, { LockScreen({}, null, {}) }) {
                    DoggyCreditApp(vm, {}, true, true, {}, {}, privacyProtected = protected.value)
                } }
            }
        }
        compose.waitUntil(10_000) { !vm.restoring && vm.home.value != null }
        compose.onNodeWithContentDescription("Abrir navegación").performClick()
        compose.onNode(hasText("Clientes") and hasClickAction()).assertIsDisplayed()
        compose.runOnIdle { protected.value = true }
        compose.onNode(hasText("Clientes") and hasClickAction()).assertDoesNotExist()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, leaves.get())
        compose.onNodeWithText("Tu información está protegida").assertExists()
        compose.runOnIdle { protected.value = false }
        compose.onNodeWithContentDescription("Abrir navegación").performClick()
        compose.onNode(hasText("Inicio") and hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)!!.path, "test-screenshots").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { stream ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}
