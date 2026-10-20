package com.doggycredit.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.data.DoggyCreditApi
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.screens.ClientScreen
import com.doggycredit.app.ui.theme.DoggyCreditTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileHistoryTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun thousandLoadedAssessmentsComposeOnlyTheWindow() {
        val base = FixtureApi()
        val api = object : DoggyCreditApi by base {
            override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?): Page<AssessmentSummary> {
                val original = base.assessments(applicantId, page, size, since)
                if (applicantId == null) return original
                return Page((1..1000).map { original.items.first().copy(id = "history-$it") }, 1000)
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoggyCreditApplication
        val vm = AppViewModel(context, FixtureSession(), api)
        compose.setContent { DoggyCreditTheme { ClientScreen("a1", vm, {}) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Perfil Financiero 360").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        if (compose.onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag("page-scroll").performScrollToNode(hasText("Preaprobado"))
        }
        val count = compose.onAllNodesWithText("Preaprobado").fetchSemanticsNodes().size
        println("HISTORY_WINDOW loaded=1000 composed_status_nodes=$count")
        assertTrue("El historial debe componer solo una ventana de las 1000 evaluaciones; compuso $count", count in 1..20)
        compose.onNodeWithTag("page-scroll").performScrollToNode(hasText("Actualizar perfil"))
        compose.onNodeWithText("Actualizar perfil").assertIsDisplayed()
        assertTrue("Las tarjetas lejanas deben salir de composición", compose.onAllNodesWithText("Preaprobado").fetchSemanticsNodes().size <= 20)
    }
}
