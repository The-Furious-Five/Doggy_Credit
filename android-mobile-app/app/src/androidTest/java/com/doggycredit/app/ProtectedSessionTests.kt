package com.doggycredit.app

import android.app.ActivityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Pantallas aisladas: no activan el bloqueo ni reemplazan la sesión real. */
class ProtectedSessionTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun protectionHidesContentClearsFocusAndRoutesBackWithoutLosingDraft() {
        val protected = mutableStateOf(false)
        val draft = mutableStateOf("BORRADOR-DE-PRUEBA")
        val focused = AtomicBoolean(false)
        val leaves = AtomicInteger()
        val normalBack = AtomicInteger()
        val contentActions = AtomicInteger()
        compose.setContent {
            DoggyCreditTheme {
                BackHandler { normalBack.incrementAndGet() }
                ProtectedSession(protected.value, { leaves.incrementAndGet() }, { LockScreen(null, null) }) {
                    Column {
                        OutlinedTextField(draft.value, { draft.value = it }, label = { Text("Borrador") },
                            modifier = Modifier.testTag("draft-field").onFocusChanged { focused.set(it.isFocused) })
                        Button(onClick = { contentActions.incrementAndGet() }) { Text("Acción del contenido") }
                    }
                }
            }
        }
        val actionCenter = compose.onNodeWithText("Acción del contenido").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("draft-field").performClick().assertIsFocused()
        compose.runOnIdle { protected.value = true }
        compose.onNodeWithTag("draft-field").assertDoesNotExist()
        compose.onNodeWithText("Acción del contenido").assertDoesNotExist()
        compose.onNodeWithText("Desbloquear").assertDoesNotExist()
        compose.onNodeWithText("Cerrar sesión").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(actionCenter) }
        compose.runOnIdle {
            assertFalse(focused.get())
            assertEquals(0, contentActions.get())
            compose.activity.onBackPressedDispatcher.onBackPressed()
            assertEquals(1, leaves.get())
            assertEquals(0, normalBack.get())
            assertTrue(protected.value)
            assertEquals("BORRADOR-DE-PRUEBA", draft.value)
            protected.value = false
        }
        compose.onNodeWithTag("draft-field").assertTextContains("BORRADOR-DE-PRUEBA")
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertEquals(1, normalBack.get())
        assertEquals(1, leaves.get())
    }

    @Test fun protectedBackBackgroundsTaskAndReturnsWithContentStillHidden() {
        val moved = AtomicBoolean(false)
        val stopped = CountDownLatch(1)
        val activity = compose.activity
        compose.setContent {
            DoggyCreditTheme {
                ProtectedSession(true, { moved.set(activity.moveTaskToBack(true)) }, { LockScreen({}, null, {}) }) {
                    Text("Contenido privado de prueba")
                }
            }
        }
        compose.onNodeWithText("Contenido privado de prueba").assertDoesNotExist()
        val task = activity.getSystemService(ActivityManager::class.java).appTasks.first { it.taskInfo.taskId == activity.taskId }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) stopped.countDown() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            activity.lifecycle.addObserver(observer)
            activity.onBackPressedDispatcher.onBackPressed()
        }
        try {
            assertTrue("Android debe aceptar el envío de la tarea al fondo", moved.get())
            assertTrue("La actividad protegida debe dejar de estar visible", stopped.await(5, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync {
                task.moveToFront()
                activity.lifecycle.removeObserver(observer)
            }
        }
        compose.waitUntil(5_000) { activity.lifecycle.currentState == Lifecycle.State.RESUMED }
        compose.onNodeWithText("Tu información está protegida").assertIsDisplayed()
        compose.onNodeWithText("Contenido privado de prueba").assertDoesNotExist()
    }

    @Test fun lockErrorKeepsBothActionsReachableWithLargeTextAndUnicode() {
        val dark = mutableStateOf(false)
        val direction = mutableStateOf(LayoutDirection.Ltr)
        val message = mutableStateOf("")
        val unlocked = AtomicInteger()
        val signedOut = AtomicInteger()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides direction.value) {
                DoggyCreditTheme(darkTheme = dark.value) {
                    LockScreen({ unlocked.incrementAndGet() }, message.value, { signedOut.incrementAndGet() })
                }
            }
        }
        val error = "No se pudo verificar tu identidad. Usa el bloqueo de pantalla de tu dispositivo o vuelve a intentarlo."
        val cases = listOf(Triple(false, LayoutDirection.Ltr, error),
            Triple(true, LayoutDirection.Rtl, "$error 🔒 Inténtalo de nuevo. تعذّر التحقق من الهوية. 身份验证失败，请重试。"))
        cases.forEachIndexed { index, (appearance, layout, text) ->
            compose.runOnIdle { dark.value = appearance; direction.value = layout; message.value = text }
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            compose.onNodeWithText("Cerrar sesión").performScrollTo().assertIsDisplayed().performClick()
            capture(if (appearance) "lock-error-dark-rtl-large-text" else "lock-error-light-large-text")
            compose.onNodeWithText("Desbloquear").performScrollTo().assertIsDisplayed().performClick()
            assertEquals(index + 1, signedOut.get())
            assertEquals(index + 1, unlocked.get())
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val base = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir") ?: context.getExternalFilesDir(null)!!.path
        val file = File(base, "test-screenshots/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
