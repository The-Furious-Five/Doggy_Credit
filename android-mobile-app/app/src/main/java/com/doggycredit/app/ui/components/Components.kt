package com.doggycredit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.theme.*

val LocalPrivacyProtected = staticCompositionLocalOf { false }
val LocalPageBottomPadding = staticCompositionLocalOf { 24.dp }

/** Variante de página para secciones o historiales que pueden acumular elementos. */
@Composable fun PageList(modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(), content: LazyListScope.() -> Unit) {
    val bottom = LocalPageBottomPadding.current
    Box(Modifier.fillMaxSize().testTag("page-viewport"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier.widthIn(max = 880.dp).fillMaxSize().imePadding().testTag("page-scroll"), state = state,
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
    }
}

/** Conserva los estados de RemoteContent sin convertir una lista lazy en una sola fila. */
fun <T> LazyListScope.remoteItems(key: String, state: Remote<T>, retry: () -> Unit, content: LazyListScope.(T) -> Unit) {
    state.error?.let { message -> item(key = "$key/error") { Notice("No pudimos actualizar la información", message, retry) } }
    if (state.loading) item(key = "$key/progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
    val value = state.value
    if (value != null) content(value)
    else if (state.loading || state.error == null) item(key = "$key/loading") { Loading() }
}

/** Acción Material sobre pino; la carga conserva contraste y bloquea la repetición. */
@Composable fun BrandButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false) {
    Button(onClick = onClick, enabled = !busy, modifier = modifier.semantics { if (busy) liveRegion = LiveRegionMode.Polite },
        colors = ButtonDefaults.buttonColors(containerColor = BrandActionColors.container, contentColor = BrandActionColors.content,
            disabledContainerColor = BrandActionColors.busyContainer, disabledContentColor = BrandActionColors.busyContent)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp).clearAndSetSemantics { }, color = BrandActionColors.busyContent, strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Text(label)
    }
}

@Composable fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val bottom = LocalPageBottomPadding.current
    Box(Modifier.fillMaxSize().testTag("page-viewport"), contentAlignment = Alignment.TopCenter) {
        Column(modifier.widthIn(max = 880.dp).fillMaxSize().verticalScroll(rememberScrollState()).testTag("page-scroll")
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottom).imePadding(),
            verticalArrangement = Arrangement.spacedBy(24.dp), content = content)
    }
}
@Composable fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(Display.locale), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}
@Composable fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary,
                         background: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(modifier.size(44.dp).clip(MaterialTheme.shapes.medium).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = color)
    }
}
@Composable fun PageIntro(eyebrow: String, title: String, subtitle: String? = null, icon: ImageVector? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        icon?.let { IconTile(it, background = MaterialTheme.colorScheme.surfaceContainerHigh) }
    }
}
/** Motivo geométrico de la marca; nunca representa una serie de datos. */
@Composable fun BrandPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Pine)) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * 1.04f, size.height * .06f)
            listOf(92.dp, 132.dp, 172.dp).forEach { radius ->
                drawCircle(Gold.copy(alpha = .12f), radius.toPx(), center, style = Stroke(1.dp.toPx()))
            }
        }
        CompositionLocalProvider(LocalContentColor provides Ivory) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
        }
    }
}
@Composable fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun Notice(title: String, message: String, onRetry: (() -> Unit)? = null) {
    Surface(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                onRetry?.let { TextButton(onClick = it, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reintentar") } }
            }
        }
    }
}
/** Campo Material con validación tras perder el foco o intentar continuar. */
@Composable fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    validationError: String? = null,
    hint: String? = null,
    submitted: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var hadFocus by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val visibleError = validationError.takeIf { submitted || touched }
    OutlinedTextField(value, onValueChange, label = { Text(label) }, enabled = enabled, singleLine = singleLine,
        keyboardOptions = keyboardOptions, isError = visibleError != null,
        supportingText = if (visibleError != null || hint != null) { { Text(visibleError ?: hint.orEmpty()) } } else null,
        modifier = modifier.onFocusChanged {
            if (it.isFocused) hadFocus = true else if (hadFocus) touched = true
        }.semantics { visibleError?.let { error(it) } })
}
@Composable fun Loading(label: String = "Cargando datos del servidor…") {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun EmptyState(title: String, description: String, actionLabel: String? = null, action: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(Icons.Rounded.Checklist, Modifier.size(64.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        actionLabel?.let { OutlinedButton(onClick = action) { Text(it) } }
    }
}
@Composable fun <T> RemoteContent(state: Remote<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    if (state.error != null) Notice("No pudimos actualizar la información", state.error, retry)
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    val value = state.value
    if (value != null) content(value)
    else if (state.loading || state.error == null) Loading()
}
@Composable fun DataRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}
@Composable fun StatusBadge(raw: String, label: String = Display.label(raw)) {
    val color = when (raw) {
        "PREAPROBADO", "APROBADO", "ACTIVO", "COMPLETA", "CONECTADO", "ESTABLE" -> MaterialTheme.colorScheme.primary
        "RECHAZADO", "FALLIDA", "REVOCADO", "ALERTA" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(color = color.copy(alpha = 0.09f), contentColor = color, shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable fun SimulatedBadge() { StatusBadge("PARCIAL", "Datos simulados") }
@Composable fun Score(score: Int?, band: String?, modifier: Modifier = Modifier, size: Dp = 96.dp, onDark: Boolean = false) {
    val color = if (onDark) Gold else when (band) { "A", "B" -> MaterialTheme.colorScheme.primary; "D", "E" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.tertiary }
    val footprint = size * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.size(footprint).semantics(mergeDescendants = true) { contentDescription = if (score == null) "Score no disponible" else "Score $score de 1000, banda ${band ?: "no disponible"}" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            drawArc(color.copy(alpha = .14f), 135f, 270f, false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round))
            score?.let { drawArc(color, 135f, 270f * (it / 1000f).coerceIn(0f, 1f), false, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            Text(score?.toString() ?: "—", style = if (size < 90.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = if (onDark) Ivory else MaterialTheme.colorScheme.onSurface)
            Text(band?.let { "Banda $it" } ?: "Sin banda", style = MaterialTheme.typography.labelSmall, color = if (onDark) Gold else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
/** Panel de score compartido por resultado y revisión; apila el contexto cuando no cabe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ScorePanel(score: Int?, band: String?, content: @Composable ColumnScope.() -> Unit) {
    BrandPanel {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val minimumWidth = minOf(160.dp * LocalDensity.current.fontScale, maxWidth)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Score(score, band, onDark = true)
                Column(Modifier.weight(1f).widthIn(min = minimumWidth), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
        }
    }
}
@Composable fun AssessmentContent(value: Assessment, mask: Boolean, capacity: Money? = null, onClient: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (value.simulated) SimulatedBadge()
        ScorePanel(value.score, value.band) {
            Text(Display.label(value.decision ?: value.status), style = MaterialTheme.typography.titleLarge)
            Text("Resultado de la evaluación", style = MaterialTheme.typography.bodySmall, color = Gold)
            if (value.status == "PARCIAL") Text("Una fuente no respondió. Este resultado requiere revisión humana.", style = MaterialTheme.typography.bodyMedium)
        }
        SectionTitle("Razones del score")
        if (value.reasons.isEmpty()) Text("El servidor no devolvió razones para esta evaluación.")
        value.reasons.take(3).forEach { reason ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
                Text(Display.reason(reason), style = MaterialTheme.typography.bodyLarge)
            }
        }
        capacity?.let { DataRow("Capacidad de pago mensual", Display.amount(Display.money(it), mask)) }
        HorizontalDivider()
        DataRow("Versión del modelo", value.modelVersion ?: "No informada")
        value.correlationId?.let { DataRow("Código de seguimiento", it) }
        onClient?.let { OutlinedButton(onClick = it) { Text("Ver Perfil Financiero 360") } }
    }
}
@Composable fun Metric(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            icon?.let { Icon(it, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
/** Mismo ancho mínimo legible para métricas en Inicio, Perfil 360 y Analítica. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun MetricGrid(values: List<Pair<String, String>>, icons: List<ImageVector> = emptyList()) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val minimumWidth = minOf(160.dp * LocalDensity.current.fontScale, maxWidth)
        FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            values.forEachIndexed { index, (label, value) ->
                Metric(label, value, Modifier.weight(1f).widthIn(min = minimumWidth).fillMaxRowHeight(), icons.getOrNull(index))
            }
        }
    }
}
@Composable fun ChipSelector(values: List<String>, selected: String?, label: (String) -> String = { Display.label(it) }, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value -> FilterChip(selected == value, { onSelect(value) }, label = { Text(label(value)) }, enabled = enabled,
            shape = CircleShape, border = FilterChipDefaults.filterChipBorder(enabled, selected == value, borderColor = MaterialTheme.colorScheme.outlineVariant),
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary)) }
    }
}
