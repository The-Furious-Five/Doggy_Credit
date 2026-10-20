package com.doggycredit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.data.*
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.components.*
import kotlinx.coroutines.*

class ResolutionModel(private val api: DoggyCreditApi, val id: String, preset: String?) : ViewModel() {
    var reviewCase by mutableStateOf(Remote<ReviewCase>()); private set
    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var decision by mutableStateOf(preset?.takeIf { it in listOf("APROBADO", "RECHAZADO") })
    var productId by mutableStateOf<String?>(null)
    var justification by mutableStateOf("")
    var saving by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var closed by mutableStateOf(false); private set
    var result by mutableStateOf<Resolution?>(null); private set
    val canSubmit get() = !saving && !closed && decision != null && justification.trim().length in 10..1000 &&
        (decision == "RECHAZADO" || products.any { it.id == productId && it.active })
    init { load() }
    fun load() {
        viewModelScope.launch {
            reviewCase = Remote(loading = true)
            try {
                var page = 1
                while (true) {
                    val response = api.reviewCases(page = page)
                    products = response.products.filter { it.active }
                    val found = response.items.firstOrNull { it.assessmentId == id }
                    if (found != null) { reviewCase = Remote(value = found); break }
                    if (response.items.isEmpty() || page * 20 >= response.total) {
                        reviewCase = Remote(error = "Este caso ya no está pendiente. Actualiza la bandeja."); closed = true; break
                    }
                    page++
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { reviewCase = Remote(error = failure.userMessage()) }
        }
    }
    fun submit(onResolved: () -> Unit) {
        if (!canSubmit) return
        saving = true; error = null
        viewModelScope.launch {
            try {
                result = api.resolve(id, NewResolution(decision!!, productId.takeIf { decision == "APROBADO" }, justification.trim()))
                closed = true; onResolved()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                when ((failure as? ApiException)?.code) {
                    "CONCURRENT_UPDATE", "ASSESSMENT_NOT_FOUND" -> { closed = true; error = "Otra persona ya resolvió este caso. Se retiró de la bandeja."; onResolved() }
                    "PRODUCT_NOT_FOUND" -> { productId = null; error = "El producto ya no está disponible. Elige uno vigente."; load() }
                    else -> error = failure.userMessage()
                }
            } finally { saving = false }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ResolveScreen(id: String, preset: String?, vm: AppViewModel, client: (String) -> Unit, assessment: (String) -> Unit, done: () -> Unit) {
    val model: ResolutionModel = viewModel(key = id, factory = factory { ResolutionModel(vm.api, id, preset) })
    PageColumn {
        model.result?.let { resolution ->
            SectionTitle("Caso resuelto", "La decisión quedó registrada en el servidor.")
            StatusBadge(resolution.decision)
            DataRow("Justificación", resolution.justification)
            DataRow("Resuelto por", resolution.resolvedBy)
            DataRow("Fecha", Display.date(resolution.resolvedAt))
            Button(onClick = done, modifier = Modifier.fillMaxWidth()) { Text("Volver a la bandeja") }
        } ?: RemoteContent(model.reviewCase, model::load) { item ->
            ScorePanel(item.score, item.band) {
                Text(Display.document(item.document, vm.mask), style = MaterialTheme.typography.titleLarge)
                Text("Caso en revisión", style = MaterialTheme.typography.bodySmall, color = com.doggycredit.app.ui.theme.Gold)
                item.purpose?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            item.reasons.take(3).forEach { Text(Display.reason(it)) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { client(item.applicantId) }) { Text("Perfil 360") }
                TextButton(onClick = { assessment(item.assessmentId) }) { Text("Ver evaluación") }
            }
            SectionTitle("Tu decisión", "No hay rechazo automático. La decisión requiere una persona y una justificación.")
            ChipSelector(listOf("APROBADO", "RECHAZADO"), model.decision, enabled = !model.saving && !model.closed) { model.decision = it }
            if (model.decision == "APROBADO") {
                SectionTitle("Producto a otorgar")
                if (model.products.isEmpty()) Notice("No hay productos vigentes", "Pide a quien administra tu institución que configure un producto en la consola web.")
                model.products.forEach { product ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth().selectable(selected = model.productId == product.id, enabled = !model.saving && !model.closed, role = Role.RadioButton, onClick = { model.productId = product.id })) {
                        Row(Modifier.fillMaxWidth().padding(12.dp)) {
                            RadioButton(selected = model.productId == product.id, onClick = null)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(Display.label(product.kind), style = MaterialTheme.typography.titleMedium)
                                Text("${Display.amount(Display.money(product.minimumAmount, product.currency), vm.mask)} a ${Display.amount(Display.money(product.maximumAmount, product.currency), vm.mask)}")
                                Text("${product.minimumTerm}–${product.maximumTerm} meses · ${Display.percent(product.annualRate.toDoubleOrNull())} anual", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(model.justification, { if (it.length <= 1000) model.justification = it }, label = { Text("Justificación obligatoria") },
                supportingText = { Text("${model.justification.trim().length}/1000 · Mínimo 10 caracteres") }, minLines = 4, modifier = Modifier.fillMaxWidth(), enabled = !model.saving && !model.closed)
            model.error?.let { Notice("No pudimos completar la resolución", it) }
            Button(onClick = { model.submit { vm.removeReview(id) } }, enabled = model.canSubmit, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (model.saving) "Guardando decisión…" else when (model.decision) { "APROBADO" -> "Aprobar caso"; "RECHAZADO" -> "Rechazar caso"; else -> "Selecciona una decisión" })
            }
            if (model.closed) OutlinedButton(onClick = done) { Text("Volver a la bandeja") }
        }
    }
}
