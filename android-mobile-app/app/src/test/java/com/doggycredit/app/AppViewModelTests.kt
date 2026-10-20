package com.doggycredit.app

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModelStore
import com.doggycredit.app.auth.SessionController
import com.doggycredit.app.auth.SessionTokens
import com.doggycredit.app.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTests {
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() { store.clear(); Dispatchers.resetMain() }

    private fun model(api: AppApi = AppApi()) = AppViewModel(Application(), TestSession(), api).also { store.put("app", it) }

    @Test fun switchingClientsRequiresAnExplicitDraftDecision() = runTest {
        val vm = model()
        vm.startEvaluation("CLIENT-A")
        vm.draft.income = "3500"
        vm.showEvaluation = false
        val original = vm.draft
        vm.startEvaluation(" client-b ")
        assertSame(original, vm.draft)
        assertEquals("CLIENT-B", vm.pendingEvaluation?.document)
        assertFalse(vm.showEvaluation)
        vm.cancelNewEvaluation()
        assertSame(original, vm.draft)
        assertNull(vm.pendingEvaluation)
        vm.startEvaluation("CLIENT-B")
        vm.resumeEvaluation()
        assertEquals("CLIENT-A", vm.draft.document)
        assertEquals("3500", vm.draft.income)
        assertNull(vm.pendingEvaluation)
        vm.showEvaluation = false
        vm.startEvaluation("CLIENT-B")
        vm.confirmNewEvaluation()
        assertEquals("CLIENT-B", vm.draft.document)
        assertEquals("", vm.draft.income)
        assertEquals(0, vm.draft.step)
        assertTrue(vm.showEvaluation)
        val replacement = vm.draft
        vm.confirmNewEvaluation()
        assertSame(replacement, vm.draft)
    }

    @Test fun completedResultsDoNotReopenAsNewEvaluations() = runTest {
        val vm = model()
        vm.draft.document = "CLIENT-A"
        vm.draft.result = Assessment(score = 710)
        vm.draft.step = 3
        vm.showEvaluation = false
        vm.startEvaluation()
        assertEquals("", vm.draft.document)
        assertEquals(0, vm.draft.step)
        assertNull(vm.draft.result)
        assertNull(vm.pendingEvaluation)
    }

    @Test fun busyDraftCannotBeDiscardedAndCreditOnlyInputIsPreserved() = runTest {
        val vm = model()
        vm.draft.amount = "12000"
        val original = vm.draft
        vm.startEvaluation("CLIENT-B")
        assertSame(original, vm.draft)
        assertNotNull(vm.pendingEvaluation)
        vm.draft.busy = true
        vm.confirmNewEvaluation()
        assertSame(original, vm.draft)
        vm.draft.busy = false
        vm.confirmNewEvaluation()
        assertEquals("CLIENT-B", vm.draft.document)
    }

    @Test fun latePaginationCannotRestoreCasesAfterRefresh() = runTest {
        val api = AppApi()
        val vm = model(api)
        val latePage = CompletableDeferred<ReviewPage>()
        api.response = { page -> if (page == 2) withContext(NonCancellable) { latePage.await() } else reviewPage("fresh", 1) }
        vm.moreReviews()
        assertTrue(vm.loadingMoreReviews)
        vm.refreshReviews()
        assertEquals(listOf("fresh"), vm.reviews.value?.items?.map { it.assessmentId })
        latePage.complete(reviewPage("stale", 2))
        advanceUntilIdle()
        assertEquals(listOf("fresh"), vm.reviews.value?.items?.map { it.assessmentId })
        assertFalse(vm.loadingMoreReviews)
        assertEquals(1, vm.reviewPage)
    }

    @Test fun paginationRejectsDuplicateTapsAndRecoversWithoutLosingCases() = runTest {
        val api = AppApi()
        val vm = model(api)
        val page = CompletableDeferred<ReviewPage>()
        api.response = { page.await() }
        vm.moreReviews(); vm.moreReviews(); vm.moreReviews()
        assertEquals(1, api.requestedPages.count { it == 2 })
        page.completeExceptionally(IOException("Sin conexión"))
        advanceUntilIdle()
        assertEquals(listOf("initial"), vm.reviews.value?.items?.map { it.assessmentId })
        assertNotNull(vm.reviewPageError)
        assertFalse(vm.loadingMoreReviews)
        api.response = { reviewPage("next", 2) }
        vm.moreReviews()
        advanceUntilIdle()
        assertEquals(listOf("initial", "next"), vm.reviews.value?.items?.map { it.assessmentId })
        assertNull(vm.reviewPageError)
    }

    @Test fun inconsistentEmptyPageAsksForRefreshInsteadOfClaimingCompletion() = runTest {
        val api = AppApi()
        val vm = model(api)
        api.response = { ReviewPage(emptyList(), 2, emptyList()) }
        vm.moreReviews()
        advanceUntilIdle()
        assertEquals(1, vm.reviewPage)
        assertEquals(1, vm.reviews.value?.items?.size)
        assertNotNull(vm.reviewPageError)
    }

    @Test fun signingOutClearsPendingClientAndDraft() = runTest {
        val vm = model()
        vm.startEvaluation("CLIENT-A")
        vm.startEvaluation("CLIENT-B")
        vm.signOut()
        assertNull(vm.pendingEvaluation)
        assertFalse(vm.showEvaluation)
        assertEquals("", vm.draft.document)
    }

    private class AppApi : RecordingApi() {
        val requestedPages = mutableListOf<Int>()
        var response: suspend (Int) -> ReviewPage = { reviewPage("initial", 2) }
        override suspend fun reviewCases(page: Int, size: Int): ReviewPage {
            requestedPages += page
            return response(page)
        }
        override suspend fun tenant() = Tenant("t1", "Institución de pruebas", "1", "IMF", "GRATIS", "BO", "BOB", true)
        override suspend fun usage() = TenantUsage(0, 500)
        override suspend fun preferences() = Preferences(true)
        override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?) = Page<AssessmentSummary>(emptyList(), 0)
        override suspend fun analytics(from: String, to: String) = AnalyticsSummary(from, to, "GRATIS", "BOB", false, 0, null, 0, 0.0, emptyList())
    }

    private class TestSession : SessionController {
        override val user = MutableStateFlow<UserSession?>(UserSession("Analista", "test", "t1", listOf("ANALYST")))
        override fun authorizationIntent() = Intent()
        override suspend fun complete(data: Intent): SessionTokens? = null
        override suspend fun restore() {}
        override suspend fun accept(value: SessionTokens) {}
        override suspend fun signOut() { user.value = null }
        override suspend fun accessToken(rejectedToken: String?) = "test-only"
        override suspend fun reject(token: String) { user.value = null }
    }

    companion object {
        private fun reviewPage(id: String, total: Int) = ReviewPage(listOf(ReviewCase(id, "a1", "CLIENT-001",
            "2026-10-01T00:00:00Z", 550, "C", "REVISION_MANUAL", false, emptyList())), total, emptyList())
    }
}
