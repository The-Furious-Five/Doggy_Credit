package com.doggycredit.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.components.*
import com.doggycredit.app.ui.theme.*

@Composable fun AnalyticsScreen(vm: AppViewModel, openPlans: () -> Unit) {
    PageColumn {
        PageIntro("Una visión más clara", "El pulso de tu cartera", "Resultados y necesidades de crédito en tu institución.", Icons.Rounded.Insights)
        ChipSelector(listOf("Este mes", "30 días", "90 días", "Este año"), vm.period, { it }, onSelect = vm::refreshAnalytics)
        RemoteContent(vm.analytics, { vm.refreshAnalytics() }) { summary ->
            Text("${Display.date(summary.from)} — ${Display.date(summary.to)}", style = MaterialTheme.typography.labelLarge)
            Metrics(listOf("Evaluaciones" to Display.number(summary.assessments), "Score promedio" to Display.number(summary.averageScore),
                "Con producto recomendado" to Display.percent(summary.recommendationPercent), "Integraciones activas" to "${summary.activeIntegrations}"))
            if (summary.assessments == 0) EmptyState("Sin evaluaciones en este período", "Elige otro período o registra una evaluación para consultar los resultados.")
            else {
                SectionTitle("Necesidades detectadas", "Destino del crédito de cada evaluación")
                if (summary.needs.any { it.count > 0 }) NeedsChart(summary.needs)
                summary.needs.forEach { need -> Breakdown(Display.label(need.category), need.percent, need.count) }
            }
            val advanced = summary.pro
            if (advanced == null) {
                Notice("Analítica avanzada", "Preaprobaciones, tiempo de decisión, recomendaciones por producto y monitoreo de modelos están disponibles según el plan de tu institución.")
                if (vm.user?.administrator == true) OutlinedButton(onClick = openPlans) { Text("Ver planes en la consola web") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Rounded.AutoGraph, background = MaterialTheme.colorScheme.tertiaryContainer, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.weight(1f)) { SectionTitle("Analítica avanzada", "Decisión, productos y salud del modelo") }
                }
                Metrics(listOf("Preaprobadas" to Display.percent(advanced.conversion), "Tiempo de decisión (mediana)" to (advanced.decisionTime?.let { "${Display.number(it)} ms" } ?: "—")))
                Metric("Monto recomendado", Display.amount(Display.money(advanced.recommendedAmount, summary.currency), vm.mask), Modifier.fillMaxWidth())
                if (advanced.products.isNotEmpty()) {
                    SectionTitle("Recomendaciones por producto")
                    advanced.products.forEach { product ->
                        Breakdown(Display.label(product.kind), product.percent, product.count)
                        Text(Display.amount(Display.money(product.recommendedAmount, summary.currency), vm.mask), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (advanced.models.isNotEmpty()) {
                    SectionTitle("Monitoreo de modelos", "Último control semanal de deriva (PSI)")
                    advanced.models.forEach { model ->
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(model.version, style = MaterialTheme.typography.titleMedium)
                                StatusBadge(model.state)
                                Text("PSI ${Display.number(model.psi, 3)} · ${model.samples} muestras actuales · ${model.baselineSamples} de referencia")
                                Text("${Display.date(model.from)} — ${Display.date(model.to)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Text("Fechas inclusivas, hora de Bolivia. La preaprobación no representa un desembolso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Metrics(values: List<Pair<String, String>>) {
    FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val icons = listOf(Icons.Rounded.Assessment, Icons.Rounded.Speed, Icons.Rounded.Recommend, Icons.Rounded.Hub)
        values.forEachIndexed { index, (label, value) -> Metric(label, value, Modifier.weight(1f).widthIn(min = 140.dp).fillMaxRowHeight(), icons[index % icons.size]) }
    }
}
@Composable private fun NeedsChart(needs: List<Need>) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.outline)
    val total = needs.sumOf { it.count.coerceAtLeast(0) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(112.dp).semantics { contentDescription = "Distribución de $total necesidades registradas. " + needs.joinToString { "${Display.label(it.category)}: ${it.count}" } }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    var start = -90f
                    needs.forEachIndexed { index, need ->
                        val sweep = if (total == 0) 0f else 360f * need.count.coerceAtLeast(0) / total
                        if (sweep > 0) drawArc(colors[index % colors.size], start + 1f, (sweep - 2f).coerceAtLeast(0f), false, style = Stroke(12.dp.toPx()))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Display.number(total), style = MaterialTheme.typography.titleLarge)
                    Text("registros", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                needs.forEachIndexed { index, need ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(colors[index % colors.size], CircleShape))
                        Text(Display.label(need.category), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
@Composable private fun Breakdown(label: String, percent: Double, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { (percent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp))
        Text("${Display.percent(percent)} · ${Display.number(count)} evaluaciones", style = MaterialTheme.typography.bodySmall)
    }
}
