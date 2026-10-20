package com.doggycredit.app

import androidx.lifecycle.ViewModelStore
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.screens.ClientDetailModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ClientHistoryTests {
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun close() { store.clear(); Dispatchers.resetMain() }
    private fun model(api: HistoryApi) = ClientDetailModel(api, "a1").also { store.put("client", it) }

    @Test fun paginationFailureRetainsRowsAndRetriesTheSamePageWithoutDuplicates() = runTest {
        val api = HistoryApi()
        val vm = model(api)
        api.response = { throw IOException("Sin conexión") }
        vm.moreHistory()
        assertEquals(listOf("first"), vm.history.value?.items?.map { it.id })
        assertNotNull(vm.historyPageError)
        api.response = { page("first", "second", total = 2) }
        vm.moreHistory()
        assertEquals(listOf(1, 2, 2), api.requests)
        assertEquals(listOf("first", "second"), vm.history.value?.items?.map { it.id })
        assertNull(vm.historyPageError)
    }

    @Test fun refreshPreventsALatePageFromReplacingTheNewHistory() = runTest {
        val api = HistoryApi()
        val vm = model(api)
        val late = CompletableDeferred<Page<AssessmentSummary>>()
        api.response = { number -> if (number == 2) withContext(NonCancellable) { late.await() } else page("fresh", total = 1) }
        vm.moreHistory()
        assertTrue(vm.loadingMore)
        vm.refresh()
        late.complete(page("stale", total = 2))
        advanceUntilIdle()
        assertEquals(listOf("fresh"), vm.history.value?.items?.map { it.id })
        assertFalse(vm.loadingMore)
    }

    @Test fun failedRefreshKeepsLoadedHistoryAndOffersRetry() = runTest {
        val api = HistoryApi()
        val vm = model(api)
        api.response = { throw IOException("Sin conexión") }
        vm.refresh()
        assertEquals(listOf("first"), vm.history.value?.items?.map { it.id })
        assertNotNull(vm.history.error)
        assertFalse(vm.history.loading)
        api.response = { page("fresh", total = 1) }
        vm.refresh()
        assertEquals(listOf("fresh"), vm.history.value?.items?.map { it.id })
        assertNull(vm.history.error)
    }

    private class HistoryApi : RecordingApi() {
        val requests = mutableListOf<Int>()
        var response: suspend (Int) -> Page<AssessmentSummary> = { page("first", total = 2) }
        override suspend fun consents(applicantId: String) = emptyList<Consent>()
        override suspend fun assessments(applicantId: String?, page: Int, size: Int, since: String?): Page<AssessmentSummary> {
            requests += page
            return response(page)
        }
    }
    companion object {
        private fun page(vararg ids: String, total: Int) = Page(ids.map {
            AssessmentSummary(it, "a1", "PREAPROBADO", "PREAPROBADO", 710, "B", "12000", 12, "2026-10-01T14:03:22Z")
        }, total)
    }
}
