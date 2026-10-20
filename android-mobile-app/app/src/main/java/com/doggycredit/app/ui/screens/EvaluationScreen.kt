package com.doggycredit.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EvaluationScreen(vm: AppViewModel, openClient: (String) -> Unit) {
    val draft = vm.draft
    val fieldFocus = remember(draft) { ApplicantField.entries.associateWith { FocusRequester() } }
    val fieldScroll = remember(draft) { ApplicantField.entries.associateWith { BringIntoViewRequester() } }
    val fieldModifiers = ApplicantField.entries.associateWith {
        Modifier.fillMaxWidth().bringIntoViewRequester(fieldScroll.getValue(it)).focusRequester(fieldFocus.getValue(it))
    }
    var validationRequest by remember(draft) { mutableIntStateOf(0) }
    LaunchedEffect(validationRequest) {
        if (validationRequest > 0 && draft.step == 0) {
            draft.applicantErrors.keys.firstOrNull()?.let {
                fieldScroll.getValue(it).bringIntoView()
                fieldFocus.getValue(it).requestFocus()
            }
        }
    }
    fun close() { vm.showEvaluation = false }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        BackHandler { close() }
        Surface(Modifier.fillMaxSize()) {
            Scaffold(Modifier.safeDrawingPadding().imePadding(), containerColor = MaterialTheme.colorScheme.background, topBar = {
                TopAppBar(title = { Text("Nueva evaluación", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, "Cerrar y conservar borrador") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            }, bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (draft.step == 0) {
                            if (draft.validationAttempted && !draft.validApplicant) Text("Revisa los campos marcados para continuar.",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        } else draft.requirement?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (draft.step in 1..2) OutlinedButton(onClick = draft::back, enabled = !draft.busy) { Text("Atrás") }
                            if (draft.step == 3) {
                                OutlinedButton(onClick = vm::restartDraft, modifier = Modifier.weight(1f)) { Text("Nueva evaluación") }
                                Button(onClick = { close() }, modifier = Modifier.weight(1f)) { Text("Listo") }
                            } else Button(onClick = {
                                if (draft.step == 0 && !draft.validateApplicant()) validationRequest++ else vm.nextEvaluation()
                            }, enabled = !draft.busy && (draft.step == 0 || draft.requirement == null), modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                if (draft.busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                                Text(if (draft.busy) "Procesando…" else if (draft.step == 2) "Evaluar cliente" else "Continuar")
                            }
                        }
                    }
                }
            }) { padding ->
                Box(Modifier.padding(padding)) {
                    PageColumn {
                        val steps = listOf("Solicitante", "Consentimiento", "Fuentes", "Resultado")
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                steps.forEachIndexed { index, _ ->
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.fillMaxWidth().height(4.dp).background(if (index <= draft.step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                        Text("0${index + 1}", style = MaterialTheme.typography.labelSmall, color = if (index == draft.step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            PageIntro("Paso ${draft.step + 1} de 4", steps[draft.step])
                        }
                        draft.error?.let { Notice("No pudimos completar este paso", "$it\nTus datos se conservan para reintentar.") }
                        when (draft.step) {
                            0 -> ApplicantStep(draft, fieldModifiers)
                            1 -> ConsentStep(draft)
                            2 -> SourcesStep(vm)
                            3 -> draft.result?.let { AssessmentContent(it, vm.mask, draft.capacity, draft.applicantId?.let { id -> { close(); openClient(id) } }) }
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ApplicantStep(draft: EvaluationDraft, fields: Map<ApplicantField, Modifier>) {
    val errors = draft.applicantErrors
    SectionTitle("Datos del solicitante", "Usa los datos que el cliente declara durante la visita.")
    ChipSelector(listOf("PERSONA", "EMPRENDEDOR", "PYME"), draft.segment, enabled = !draft.busy) { draft.segment = it }
    ValidatedTextField(draft.document, { if (it.length <= 64) draft.document = it }, "CI o NIT", fields.getValue(ApplicantField.DOCUMENT),
        validationError = errors[ApplicantField.DOCUMENT], hint = "De 3 a 64 caracteres.", submitted = draft.validationAttempted,
        enabled = !draft.busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii))
    ValidatedTextField(draft.activity, { if (it.length <= 120) draft.activity = it }, "Actividad económica", fields.getValue(ApplicantField.ACTIVITY),
        validationError = errors[ApplicantField.ACTIVITY], hint = "De 2 a 120 caracteres.", submitted = draft.validationAttempted, enabled = !draft.busy, singleLine = false)
    NumberField("Antigüedad de la actividad (meses)", draft.seniority, { draft.seniority = it }, false, !draft.busy,
        fields.getValue(ApplicantField.SENIORITY), errors[ApplicantField.SENIORITY], "De 0 a 1200 meses.", draft.validationAttempted)
    NumberField("Ingreso mensual declarado (Bs)", draft.income, { draft.income = it }, enabled = !draft.busy,
        modifier = fields.getValue(ApplicantField.INCOME), validationError = errors[ApplicantField.INCOME], hint = "Mayor que 0. Puedes usar coma decimal.", submitted = draft.validationAttempted)
    HorizontalDivider()
    SectionTitle("Solicitud de crédito")
    NumberField("Monto solicitado (Bs, opcional)", draft.amount, { draft.amount = it }, enabled = !draft.busy,
        modifier = fields.getValue(ApplicantField.AMOUNT), validationError = errors[ApplicantField.AMOUNT], hint = "Mayor que 0 o vacío si aún no está definido.", submitted = draft.validationAttempted)
    NumberField("Plazo (meses)", draft.term, { draft.term = it }, false, !draft.busy,
        fields.getValue(ApplicantField.TERM), errors[ApplicantField.TERM], "De 1 a 360 meses.", draft.validationAttempted)
    Text("Destino del crédito", style = MaterialTheme.typography.titleSmall)
    if (draft.validationAttempted) errors[ApplicantField.PURPOSE]?.let {
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        creditPurposes.forEachIndexed { index, purpose ->
            val modifier = if (index == 0) fields.getValue(ApplicantField.PURPOSE) else Modifier.fillMaxWidth()
            OutlinedCard(modifier = modifier.selectable(selected = draft.purpose == purpose, enabled = !draft.busy, role = Role.RadioButton, onClick = { draft.purpose = purpose }),
                colors = CardDefaults.outlinedCardColors(containerColor = if (draft.purpose == purpose) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (draft.purpose == purpose) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(draft.purpose == purpose, onClick = null)
                    Text(purpose, Modifier.padding(start = 12.dp))
                }
            }
        }
    }
}
@Composable private fun NumberField(label: String, value: String, changed: (String) -> Unit, decimal: Boolean = true, enabled: Boolean = true,
    modifier: Modifier = Modifier.fillMaxWidth(), validationError: String? = null, hint: String? = null, submitted: Boolean = false) {
    ValidatedTextField(value, { input -> if (input.length <= 18 && input.all { it.isDigit() || decimal && (it == '.' || it == ',') }) changed(input) },
        label, modifier, validationError, hint, submitted, enabled, keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number))
}
@Composable internal fun ConsentStep(draft: EvaluationDraft) {
    SectionTitle("Autorización del cliente", "Explica cada consulta y marca únicamente lo que el cliente autorice.")
    if (draft.existingApplicant) Notice("Cliente ya registrado", "Se reutilizarán sus datos declarados guardados en el servidor. La solicitud de crédito corresponde a esta nueva evaluación.")
    ConsentOption("Buró de crédito · Obligatorio", "Historial de pagos y deudas vigentes", draft.bureau, !draft.busy, { draft.bureau = it })
    ConsentOption("Banco", "Cuentas y movimientos autorizados", draft.bank, !draft.busy, { draft.bank = it })
    ConsentOption("Billetera móvil", "Flujos de ingresos de la billetera", draft.wallet, !draft.busy, { draft.wallet = it })
    Notice("Consentimiento por 90 días", "Finalidad: evaluación de crédito. El cliente puede revocarlo desde su perfil. No se consultarán las fuentes que no autorice.")
}
@Composable private fun ConsentOption(title: String, detail: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = change),
        colors = CardDefaults.outlinedCardColors(containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked, onCheckedChange = null, enabled = enabled)
            Column(Modifier.padding(start = 12.dp)) { Text(title, style = MaterialTheme.typography.titleSmall); Text(detail, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
@Composable private fun SourcesStep(vm: AppViewModel) {
    val draft = vm.draft
    SectionTitle("Fuentes de tu institución", "La API consultará únicamente las fuentes conectadas y autorizadas.")
    RemoteContent(draft.sources, vm::reloadSources) { sources ->
        if (sources.isEmpty()) Notice("Sin integraciones informadas", "El servidor determinará las fuentes disponibles al procesar la evaluación.")
        sources.forEach { source ->
            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Display.label(source.source), style = MaterialTheme.typography.titleMedium)
                    StatusBadge(source.status)
                    if (source.source in listOf("BURO", "BANCO", "WALLET")) {
                        val authorized = when (source.source) { "BURO" -> draft.bureau; "BANCO" -> draft.bank; else -> draft.wallet }
                        Text(if (authorized) "Autorizada por el cliente" else "Sin autorización: no se consultará", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    HorizontalDivider()
    SectionTitle("Antes de evaluar")
    DataRow("Documento", Display.document(draft.document, vm.mask))
    DataRow("Monto solicitado", if (draft.amount.isBlank()) "No indicado" else Display.amount(Display.money(Display.apiAmount(draft.amount)), vm.mask))
    DataRow("Plazo", "${draft.term} meses")
    DataRow("Destino", draft.purpose ?: "—")
}
