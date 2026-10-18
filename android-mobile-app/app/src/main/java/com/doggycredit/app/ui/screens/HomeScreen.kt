package com.doggycredit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.R
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.components.*
import com.doggycredit.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

@Composable fun HomeScreen(vm: AppViewModel, client: (String) -> Unit, resolve: (String, String?) -> Unit, reviewTab: () -> Unit) {
    PageColumn {
        val now = ZonedDateTime.now(Display.zone)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow(Display.date(now.toLocalDate().toString()))
            val greeting = when (now.hour) { in 5..11 -> "Buenos días"; in 12..18 -> "Buenas tardes"; else -> "Buenas noches" }
            Text("$greeting,\n${vm.user?.name?.substringBefore(' ') ?: ""}.", style = MaterialTheme.typography.headlineLarge)
            vm.institution.value?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccountBalance, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(it.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        UsageOverview(vm.usage.value, vm.institution.value?.plan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Clientes en cartera", vm.clients.value?.total?.let(Display::number) ?: "—", Modifier.weight(1f), Icons.Rounded.PeopleOutline)
            Metric("Casos pendientes", vm.reviews.value?.total?.let(Display::number) ?: "—", Modifier.weight(1f), Icons.AutoMirrored.Rounded.Rule)
        }
        listOf(vm.institution.error, vm.usage.error, vm.preferences.error).filterNotNull().firstOrNull()?.let {
            Notice("No pudimos actualizar tu institución", it, vm::refreshSession)
        }
        vm.usage.value?.let { usage ->
            if (usage.warning) Notice(if (usage.exhausted) "Alcanzaste el límite del mes" else "Tu cuota está por agotarse",
                "${Display.number(usage.used)} de ${Display.number(usage.limit)} evaluaciones. ${if (usage.exhausted) "Las nuevas evaluaciones se reanudan el próximo mes." else "Al alcanzar el límite se pausarán las nuevas evaluaciones."}")
        }
        if (vm.draft.inProgress) DraftCard(vm)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Eyebrow("Tu próxima decisión"); SectionTitle("Por resolver") }
            TextButton(onClick = reviewTab) { Text("Ver bandeja"); Icon(Icons.Rounded.ChevronRight, null) }
        }
        RemoteContent(vm.reviews, { vm.refreshReviews() }) { queue ->
            if (queue.items.isEmpty()) EmptyState("Todo al día", "Cuando una evaluación requiera revisión manual aparecerá aquí.")
            else queue.items.take(2).forEach { item -> ReviewCard(item, vm.mask, { resolve(item.assessmentId, null) }, { client(item.applicantId) }) }
        }
        SectionTitle("Evaluaciones recientes", "Las últimas decisiones de tu institución")
        RemoteContent(vm.home, { vm.refreshHome() }) { items ->
            if (items.isEmpty()) EmptyState("Todavía no hay evaluaciones", "Las evaluaciones registradas aparecerán aquí.", "Nueva evaluación", { vm.startEvaluation() })
            else {
                val applicants = remember(items) { mutableStateMapOf<String, Applicant>() }
                LaunchedEffect(items) {
                    items.map { it.applicantId }.distinct().forEach { id -> launch {
                        try { applicants[id] = vm.api.applicant(id) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* La evaluación sigue disponible mediante su ID real. */ }
                    } }
                }
                items.forEach { summary -> AssessmentSummaryCard(summary, applicants[summary.applicantId]?.document?.let { Display.document(it, vm.mask) } ?: "Ver cliente", { client(summary.applicantId) }) }
            }
        }
    }
}
@Composable fun UsageOverview(usage: TenantUsage?, plan: String?) {
    BrandPanel {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Actividad de este mes", color = Gold)
                Text(usage?.used?.let(Display::number) ?: "—", style = MaterialTheme.typography.displaySmall, color = Ivory)
                Text("evaluaciones registradas", style = MaterialTheme.typography.bodyMedium, color = Ivory.copy(alpha = .8f))
            }
            Image(painterResource(R.drawable.brand_mark), null, Modifier.size(82.dp))
        }
        HorizontalDivider(color = Ivory.copy(alpha = .15f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(plan?.let { "Plan ${Display.label(it)}" } ?: "Tu plan", style = MaterialTheme.typography.labelLarge, color = Gold)
            usage?.let { Text(if (it.unlimited) "Sin límite mensual" else "Límite: ${Display.number(it.limit)} al mes", style = MaterialTheme.typography.bodySmall, color = Ivory.copy(alpha = .8f), modifier = Modifier.weight(1f).padding(start = 16.dp)) }
        }
        usage?.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(4.dp), color = Gold, trackColor = Ivory.copy(alpha = .15f), gapSize = 0.dp) }
    }
}
@Composable fun DraftCard(vm: AppViewModel) {
    var confirm by remember { mutableStateOf(false) }
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .25f)), border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = .2f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Evaluación en curso", style = MaterialTheme.typography.titleMedium)
            Text("${Display.document(vm.draft.document, vm.mask)} · Paso ${vm.draft.step + 1} de 4", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.showEvaluation = true }) { Text("Continuar") }
                TextButton(onClick = { confirm = true }, enabled = !vm.draft.busy) { Text("Descartar") }
            }
        }
    }
    if (confirm && !LocalPrivacyProtected.current) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("¿Descartar el borrador?") }, text = { Text("Se borrarán los datos escritos. Lo que ya se registró se conserva en el servidor.") },
        confirmButton = { TextButton(onClick = { vm.discardDraft(); confirm = false }) { Text("Descartar") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Conservar") } })
}
@Composable fun AssessmentSummaryCard(summary: AssessmentSummary, title: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Eyebrow("Evaluación")
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(Display.date(summary.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusBadge(summary.decision ?: summary.status)
            }
            Score(summary.score, summary.band, size = 78.dp)
        }
    }
}
@Composable fun ReviewCard(item: ReviewCase, mask: Boolean, onResolve: () -> Unit, onClient: () -> Unit, preset: ((String) -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("Caso pendiente", color = MaterialTheme.colorScheme.tertiary)
                    Text(Display.document(item.document, mask), style = MaterialTheme.typography.titleMedium)
                    Text(Display.date(item.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Acciones del caso") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ver Perfil 360") }, onClick = { menu = false; onClient() })
                        preset?.let {
                            DropdownMenuItem(text = { Text("Aprobar…") }, onClick = { menu = false; it("APROBADO") })
                            DropdownMenuItem(text = { Text("Rechazar…") }, onClick = { menu = false; it("RECHAZADO") })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatusBadge(if (item.partial) "PARCIAL" else item.motive)
                    Text(item.reasons.firstOrNull()?.let(Display::reason) ?: "Sin razones informadas", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Score(item.score, item.band, size = 78.dp)
            }
            item.purpose?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("${Display.amount(Display.money(item.requestedAmount), mask)} · ${item.termMonths?.let { "$it meses" } ?: "Plazo no indicado"}", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            TextButton(onClick = onResolve, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                Text("Resolver caso", Modifier.weight(1f)); Icon(Icons.Rounded.ArrowOutward, null, Modifier.size(20.dp))
            }
        }
    }
}
