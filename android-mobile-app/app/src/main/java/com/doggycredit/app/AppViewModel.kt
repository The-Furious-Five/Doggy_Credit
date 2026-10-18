package com.doggycredit.app

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.doggycredit.app.auth.SessionController
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import kotlinx.coroutines.*
import java.time.LocalDate

class AppViewModel @JvmOverloads constructor(
    application: Application,
    val auth: SessionController = (application as DoggyCreditApplication).auth,
    val api: DoggyCreditApi = (application as DoggyCreditApplication).api
) : AndroidViewModel(application) {
    var user by mutableStateOf<UserSession?>(null); private set
    var restoring by mutableStateOf(true); private set
    var signingIn by mutableStateOf(false); private set
    var notice by mutableStateOf<String?>(null)
    var institution by mutableStateOf(Remote<Tenant>()); private set
    var usage by mutableStateOf(Remote<TenantUsage>()); private set
    var preferences by mutableStateOf(Remote<Preferences>()); private set
    var home by mutableStateOf(Remote<List<AssessmentSummary>>()); private set
    var clients by mutableStateOf(Remote<Page<Applicant>>()); private set
    var segment by mutableStateOf<String?>(null); private set
    var clientPage = 1; private set
    var loadingMoreClients by mutableStateOf(false); private set
    var clientPageError by mutableStateOf<String?>(null); private set
    var reviews by mutableStateOf(Remote<ReviewPage>()); private set
    var reviewPage = 1; private set
    var loadingMoreReviews by mutableStateOf(false); private set
    var reviewPageError by mutableStateOf<String?>(null); private set
    var reviewFilter by mutableStateOf("Todos")
    var analytics by mutableStateOf(Remote<AnalyticsSummary>()); private set
    var period by mutableStateOf("Este mes"); private set
    var search by mutableStateOf(Remote<Page<Applicant>>()); private set
    var searchDocument by mutableStateOf(""); private set
    var draft by mutableStateOf(EvaluationDraft()); private set
    var showEvaluation by mutableStateOf(false)
    private var sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
    private var searchJob: Job? = null
    private var analyticsJob: Job? = null
    private var clientsJob: Job? = null
    val mask get() = user?.shouldMask(preferences.value) ?: true

    init {
        viewModelScope.launch {
            auth.restore()
            user = auth.user.value
            restoring = false
            if (user != null) refreshSession()
            auth.user.collect { next ->
                if (user != null && next == null) { clearSession(); notice = "Tu sesión terminó. Inicia sesión de nuevo para continuar." }
                user = next
            }
        }
    }
    fun completeSignIn(data: Intent?) {
        if (data == null || signingIn) return
        signingIn = true; notice = null
        viewModelScope.launch {
            try {
                val tokens = auth.complete(data) ?: return@launch
                auth.accept(tokens)
                val tenant = api.tenant()
                institution = Remote(value = tenant)
                user = auth.user.value
                refreshSession()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { auth.signOut(); notice = failure.userMessage() }
            finally { signingIn = false }
        }
    }
    fun signOut() {
        clearSession()
        user = null
        viewModelScope.launch { auth.signOut() }
    }
    private fun clearSession() {
        sessionJob.cancel()
        sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
        sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
        draft = EvaluationDraft(); showEvaluation = false
        institution = Remote(); usage = Remote(); preferences = Remote(); home = Remote(); clients = Remote()
        reviews = Remote(); analytics = Remote(); search = Remote(); searchDocument = ""
        clientPage = 1; reviewPage = 1; segment = null; reviewFilter = "Todos"
        searchJob?.cancel(); analyticsJob?.cancel(); clientsJob?.cancel()
    }
    private fun <T> load(current: Remote<T>, set: (Remote<T>) -> Unit, block: suspend () -> T): Job = sessionScope.launch {
        set(current.copy(loading = true, error = null))
        try { set(Remote(value = block())) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { set(current.copy(loading = false, error = failure.userMessage(), status = (failure as? ApiException)?.status)) }
    }
    fun refreshSession() {
        load(institution, { institution = it }) { api.tenant() }
        load(usage, { usage = it }) { api.usage() }
        load(Remote<Preferences>(), { preferences = it }) { api.preferences() }
        refreshHome(); refreshReviews(); refreshClients(); refreshAnalytics()
    }
    fun refreshHome() = load(home, { home = it }) { api.assessments(size = 6).items }
    fun refreshClients(selected: String? = segment) {
        val changed = segment != selected
        segment = selected; clientPageError = null
        clientsJob?.cancel()
        val current = if (changed) Remote() else clients
        clientsJob = load(current, { clients = it }) { api.applicants(segment = selected).also { clientPage = 1 } }
    }
    fun moreClients() {
        val page = clients.value ?: return
        if (loadingMoreClients || page.items.size >= page.total) return
        loadingMoreClients = true; clientPageError = null
        val selected = segment
        sessionScope.launch {
            try {
                val next = api.applicants(page = clientPage + 1, segment = selected)
                if (segment == selected) { clients = Remote(value = Page((page.items + next.items).distinctBy { it.id }, next.total)); clientPage++ }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { clientPageError = failure.userMessage() }
            finally { loadingMoreClients = false }
        }
    }
    fun refreshReviews() = load(reviews, { reviews = it }) { api.reviewCases().also { reviewPage = 1 } }
    fun moreReviews() {
        val page = reviews.value ?: return
        if (loadingMoreReviews || page.items.size >= page.total) return
        loadingMoreReviews = true; reviewPageError = null
        sessionScope.launch {
            try {
                val next = api.reviewCases(page = reviewPage + 1)
                reviews = Remote(value = ReviewPage((page.items + next.items).distinctBy { it.assessmentId }, next.total, next.products)); reviewPage++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { reviewPageError = failure.userMessage() }
            finally { loadingMoreReviews = false }
        }
    }
    fun removeReview(id: String) {
        reviews.value?.let { page -> reviews = Remote(value = page.copy(items = page.items.filterNot { it.assessmentId == id }, total = (page.total - 1).coerceAtLeast(0))) }
        refreshHome()
    }
    fun search(document: String) {
        val normalized = document.trim().uppercase(java.util.Locale.ROOT)
        if (normalized.length < 3) return
        searchJob?.cancel(); searchDocument = normalized
        searchJob = load(Remote(), { search = it }) { api.applicants(size = 1, document = normalized) }
    }
    fun clearSearch() { searchJob?.cancel(); search = Remote(); searchDocument = "" }
    fun refreshAnalytics(selected: String = period) {
        period = selected; analyticsJob?.cancel()
        val today = LocalDate.now(Display.zone)
        val from = when (selected) {
            "30 días" -> today.minusDays(29); "90 días" -> today.minusDays(89)
            "Este año" -> today.withDayOfYear(1); else -> today.withDayOfMonth(1)
        }
        analyticsJob = load(Remote(), { analytics = it }) { api.analytics(from.toString(), today.toString()) }
    }
    fun startEvaluation(document: String? = null) {
        if (!draft.inProgress && document != null) { draft = EvaluationDraft(); draft.document = document }
        showEvaluation = true
    }
    fun nextEvaluation() { sessionScope.launch { draft.next(api) { refreshSession() } } }
    fun reloadSources() { sessionScope.launch { draft.loadSources(api) } }
    fun discardDraft() { if (!draft.busy) { draft = EvaluationDraft(); showEvaluation = false } }
    fun restartDraft() { if (!draft.busy) draft = EvaluationDraft() }
}
