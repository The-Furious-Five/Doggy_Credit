package com.doggycredit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.components.*
import com.doggycredit.app.ui.theme.*
import kotlinx.coroutines.*

fun <T : ViewModel> factory(create: () -> T): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
}
class ClientDetailModel(private val api: DoggyCreditApi, private val id: String) : ViewModel() {
    var detail by mutableStateOf(Remote<Applicant>()); private set
    var consents by mutableStateOf(Remote<List<Consent>>()); private set
    var history by mutableStateOf(Remote<Page<AssessmentSummary>>()); private set
    var revoking by mutableStateOf(false); private set
    var revokeError by mutableStateOf<String?>(null); private set
    var revokeSuccess by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var historyPageError by mutableStateOf<String?>(null); private set
    private var page = 1
    private var historyJob: Job? = null
    private var historyGeneration = 0
    init { refresh() }
    fun refresh() {
        viewModelScope.launch {
            detail = detail.copy(loading = true, error = null)
            try { detail = Remote(value = api.applicant(id)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { detail = Remote(error = error.userMessage()) }
        }
        refreshConsents()
        val generation = ++historyGeneration
        historyJob?.cancel()
        loadingMore = false
        historyPageError = null
        historyJob = viewModelScope.launch {
            history = history.copy(loading = true, error = null)
            try {
                val result = api.assessments(applicantId = id)
                if (generation == historyGeneration) { history = Remote(value = result); page = 1 }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == historyGeneration) history = history.copy(loading = false, error = error.userMessage()) }
        }
    }
    private fun refreshConsents() = viewModelScope.launch {
        consents = consents.copy(loading = true, error = null)
        try { consents = Remote(value = api.consents(id)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { consents = Remote(error = error.userMessage()) }
    }
    fun moreHistory() {
        val current = history.value ?: return
        if (history.loading || loadingMore || current.items.size >= current.total) return
        val generation = historyGeneration
        loadingMore = true; historyPageError = null
        historyJob = viewModelScope.launch {
            try {
                val next = api.assessments(applicantId = id, page = page + 1)
                if (generation == historyGeneration) { history = Remote(value = Page((current.items + next.items).distinctBy { it.id }, next.total)); page++ }
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (generation == historyGeneration) historyPageError = error.userMessage() }
            finally { if (generation == historyGeneration) loadingMore = false }
        }
    }
    fun revoke() {
        if (revoking) return
        revoking = true; revokeError = null; revokeSuccess = false
        viewModelScope.launch {
            try {
                consents.value.orEmpty().filter { it.status == "ACTIVO" }.forEach { api.revokeConsent(it.id) }
                consents = Remote(value = api.consents(id)); revokeSuccess = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { revokeError = error.userMessage(); refreshConsents() }
            finally { revoking = false }
        }
    }
}
@Composable fun ClientScreen(id: String, vm: AppViewModel, onAssessment: (String) -> Unit) {
    val model: ClientDetailModel = viewModel(key = id, factory = factory { ClientDetailModel(vm.api, id) })
    var confirmRevoke by remember { mutableStateOf(false) }
    PageList {
        remoteItems("detail", model.detail, model::refresh) { applicant ->
            item(key = "identity") { BrandPanel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconTile(Icons.Rounded.PersonOutline, color = Pine, background = Gold)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Eyebrow(Display.label(applicant.segment ?: "PERSONA"), color = Gold)
                        Text(Display.document(applicant.document, vm.mask), style = MaterialTheme.typography.headlineSmall)
                        Text(applicant.activity, style = MaterialTheme.typography.bodyMedium, color = Ivory.copy(alpha = .8f))
                    }
                }
            } }
            item(key = "evaluate") { Button(onClick = { vm.startEvaluation(applicant.document) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Nueva evaluación") } }
            item(key = "profile-heading") { SectionTitle("Perfil Financiero 360") }
            val profile = applicant.profile
            if (profile == null) item(key = "profile-empty") { EmptyState("Sin perfil consolidado", "Realiza una evaluación para consultar las fuentes autorizadas.") }
            else {
                item(key = "profile-metrics") { MetricGrid(listOf("Ingreso mensual" to Display.amount(Display.money(profile.monthlyIncome), vm.mask),
                    "Capacidad de pago" to Display.amount(Display.money(profile.paymentCapacity), vm.mask)),
                    listOf(Icons.Rounded.AccountBalanceWallet, Icons.Rounded.Savings)) }
                item(key = "profile-data") { Card {
                    Column(Modifier.padding(16.dp)) {
                        DataRow("Origen del ingreso", when (profile.incomeOrigin) { "BANCO" -> "Promedio de 6 meses en el banco"; "WALLET" -> "Promedio de 6 meses en la billetera"; else -> "Declarado por el cliente" })
                        DataRow("Deuda vigente", Display.amount(Display.money(profile.debt), vm.mask))
                        DataRow("Cuota mensual", Display.amount(Display.money(profile.installment), vm.mask))
                        DataRow("Endeudamiento", profile.debtPercent?.toDoubleOrNull()?.let(Display::percent) ?: "—")
                        DataRow("Obligaciones", Display.number(profile.obligations))
                        DataRow("Antigüedad en el sistema financiero", profile.financialSeniority?.let { "$it meses" } ?: "No disponible")
                        DataRow("Consolidado el", Display.date(profile.createdAt))
                    }
                } }
                item(key = "sources-heading") { SectionTitle("Fuentes consultadas") }
                itemsIndexed(profile.sources, key = { index, source -> "source/$index/${source.source}" }, contentType = { _, _ -> "source" }) { _, source ->
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(Display.label(source.source), style = MaterialTheme.typography.titleMedium)
                            StatusBadge(source.state)
                            source.queriedAt?.let { Text(Display.date(it), style = MaterialTheme.typography.bodySmall) }
                            source.sha256?.let { Text("SHA-256: $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                }
            }
            item(key = "history-heading") { SectionTitle("Historial de evaluaciones") }
            remoteItems("history", model.history, model::refresh) { history ->
                if (history.items.isEmpty()) item(key = "history-empty") { Text("Sin evaluaciones registradas.") }
                items(history.items, key = { "assessment/${it.id}" }, contentType = { "assessment" }) { summary ->
                    AssessmentSummaryCard(summary, Display.date(summary.createdAt)) { onAssessment(summary.id) }
                }
                if (history.items.size < history.total) item(key = "history-next/${history.items.size}") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        model.historyPageError?.let { Notice("No pudimos cargar más evaluaciones", it) }
                        OutlinedButton(onClick = model::moreHistory, enabled = !model.loadingMore && !model.history.loading,
                            modifier = Modifier.fillMaxWidth()) { Text(if (model.loadingMore) "Cargando…" else "Cargar más evaluaciones") }
                    }
                }
            }
            item(key = "consents-heading") { SectionTitle("Consentimientos") }
            remoteItems("consents", model.consents, model::refresh) { consents ->
                if (consents.isEmpty()) item(key = "consents-empty") { Text("Sin consentimientos registrados.") }
                items(consents, key = { "consent/${it.id}" }, contentType = { "consent" }) { consent ->
                    OutlinedCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(consent.status)
                            Text(consent.sources.joinToString(" · ") { Display.label(it) })
                            Text(consent.purpose, style = MaterialTheme.typography.bodyMedium)
                            DataRow("Vence", Display.date(consent.expiresAt))
                            consent.revokedAt?.let { DataRow("Revocado", Display.date(it)) }
                        }
                    }
                }
                if (consents.any { it.status == "ACTIVO" }) item(key = "revoke") { OutlinedButton(onClick = { confirmRevoke = true }, enabled = !model.revoking, modifier = Modifier.fillMaxWidth()) {
                    Text(if (model.revoking) "Revocando…" else "Revocar consentimientos vigentes")
                } }
            }
            model.revokeError?.let { message -> item(key = "revoke-error") { Notice("Revisa los consentimientos vigentes", message) } }
            if (model.revokeSuccess) item(key = "revoke-success") { Notice("Consentimientos revocados", "No se volverán a consultar estas fuentes. El perfil ya consolidado se conserva.") }
            item(key = "declared-heading") { SectionTitle("Datos declarados") }
            item(key = "declared-data") { Card {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    DataRow("Ingreso mensual", Display.amount(Display.money(applicant.monthlyIncome, applicant.currency), vm.mask))
                    DataRow("Antigüedad en la actividad", "${applicant.seniorityMonths} meses")
                    DataRow("Destino del crédito", applicant.purpose)
                    DataRow("Cliente desde", Display.date(applicant.createdAt))
                }
            } }
            item(key = "refresh") { TextButton(onClick = model::refresh) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Actualizar perfil") } }
        }
    }
    if (confirmRevoke && !LocalPrivacyProtected.current) AlertDialog(onDismissRequest = { confirmRevoke = false }, title = { Text("¿Revocar los consentimientos?") },
        text = { Text("Se detendrán las nuevas consultas a las fuentes autorizadas. El perfil consolidado y el historial se conservarán.") },
        confirmButton = { TextButton(onClick = { confirmRevoke = false; model.revoke() }) { Text("Revocar") } },
        dismissButton = { TextButton(onClick = { confirmRevoke = false }) { Text("Cancelar") } })
}
@Composable fun AssessmentScreen(id: String, vm: AppViewModel) {
    var state by remember(id) { mutableStateOf(Remote<Assessment>(loading = true)) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, revision) {
        state = state.copy(loading = true, error = null)
        try { state = Remote(value = vm.api.assessment(id)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { state = Remote(error = error.userMessage()) }
    }
    PageColumn { RemoteContent(state, { revision++ }) { AssessmentContent(it, vm.mask) } }
}
