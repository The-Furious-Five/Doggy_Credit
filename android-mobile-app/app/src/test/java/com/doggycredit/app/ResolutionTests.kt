package com.doggycredit.app

import com.doggycredit.app.data.ApiException
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.screens.ResolutionModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolutionTests {
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() { Dispatchers.resetMain() }
    @Test fun approvalRequiresExplicitDecisionProductAndJustification() = runTest {
        val model = ResolutionModel(ReviewApi(), "e1", null)
        assertFalse(model.canSubmit); model.decision = "APROBADO"; model.justification = "Ingresos verificados en la visita"
        assertFalse(model.canSubmit); model.productId = "p1"; assertTrue(model.canSubmit)
        model.justification = "Corto"; assertFalse(model.canSubmit)
    }
    @Test fun rejectionNeverSendsProductAndCannotBeSubmittedTwice() = runTest {
        val api = ReviewApi(); val model = ResolutionModel(api, "e1", "RECHAZADO")
        model.productId = "p1"; model.justification = "Capacidad de pago insuficiente"
        var notified = 0; model.submit { notified++ }; advanceUntilIdle()
        assertNull(api.sent?.productId); assertEquals("RECHAZADO", model.result?.decision); assertTrue(model.closed)
        model.submit { notified++ }; advanceUntilIdle(); assertEquals(1, notified)
    }
    @Test fun concurrentResolutionClosesCaseAndUpdatesInbox() = runTest {
        val api = ReviewApi().apply { failure = ApiException(409, "CONCURRENT_UPDATE", "Resuelto") }
        val model = ResolutionModel(api, "e1", "RECHAZADO"); model.justification = "Ingresos insuficientes"
        var notified = false; model.submit { notified = true }; advanceUntilIdle()
        assertTrue(model.closed); assertTrue(notified); assertFalse(model.canSubmit); assertNotNull(model.error)
    }
    @Test fun deactivatedProductMustBeSelectedAgain() = runTest {
        val api = ReviewApi().apply { failure = ApiException(404, "PRODUCT_NOT_FOUND", "No disponible") }
        val model = ResolutionModel(api, "e1", "APROBADO"); model.justification = "Ingresos verificados"; model.productId = "p1"
        model.submit {}; advanceUntilIdle(); assertNull(model.productId); assertFalse(model.canSubmit); assertNotNull(model.error)
    }
    @Test fun missingCaseIsNotPresentedAsPending() = runTest {
        val api = ReviewApi().apply { pending = false }
        val model = ResolutionModel(api, "e1", null); advanceUntilIdle()
        assertTrue(model.closed); assertNull(model.reviewCase.value); assertNotNull(model.reviewCase.error)
    }
    private class ReviewApi : RecordingApi() {
        var pending = true; var failure: Exception? = null; var sent: NewResolution? = null
        override suspend fun reviewCases(page: Int, size: Int) = ReviewPage(
            if (pending) listOf(ReviewCase("e1", "a1", "CLIENT-001", "2026-10-01T00:00:00Z", 550, "C", "REVISION_MANUAL", false, listOf("SIN_MORA_12M"))) else emptyList(),
            if (pending) 1 else 0, listOf(Product("p1", "MICROCREDITO", "BOB", "1000.00", "50000.00", 6, 36, "10.00", "C", true))
        )
        override suspend fun resolve(id: String, body: NewResolution): Resolution {
            sent = body; failure?.let { throw it }
            return Resolution(id, body.decision, body.productId, body.justification, "analyst", "2026-10-14T00:00:00Z")
        }
    }
}
