package com.doggycredit.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filter
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.model.*
import com.doggycredit.app.ui.components.*
import com.doggycredit.app.ui.theme.*

@Composable fun ClientsScreen(vm: AppViewModel, onClient: (String) -> Unit) {
    val bottom = LocalPageBottomPadding.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 880.dp).fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PageIntro("Relaciones que crecen", "Tu cartera", vm.clients.value?.let { "${Display.number(it.total)} ${if (it.total == 1) "cliente" else "clientes"} · Un perfil para cada historia" }, Icons.Rounded.PeopleOutline) }
            item { ChipSelector(listOf("TODOS", "PERSONA", "EMPRENDEDOR", "PYME"), vm.segment ?: "TODOS", { if (it == "TODOS") "Todos" else Display.label(it) }) { vm.refreshClients(it.takeUnless { it == "TODOS" }) } }
            vm.clients.error?.let { item { Notice("No pudimos cargar los clientes", it, { vm.refreshClients() }) } }
            if (vm.clients.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            val page = vm.clients.value
            if (page != null) {
                if (page.items.isEmpty()) item { EmptyState("Sin clientes", "Registra un cliente al realizar una evaluación.", "Nueva evaluación", { vm.startEvaluation() }) }
                items(page.items, key = { it.id }) { applicant -> ApplicantCard(applicant, vm.mask) { onClient(applicant.id) } }
                if (page.items.size < page.total) item {
                    vm.clientPageError?.let { Notice("No pudimos cargar más clientes", it) }
                    OutlinedButton(onClick = vm::moreClients, enabled = !vm.loadingMoreClients, modifier = Modifier.fillMaxWidth()) {
                        if (vm.loadingMoreClients) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Cargar más clientes")
                    }
                }
            } else if (!vm.clients.loading && vm.clients.error == null) item { Loading() }
        }
    }
}
@Composable fun ApplicantCard(applicant: Applicant, mask: Boolean, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(when (applicant.segment) { "EMPRENDEDOR" -> Icons.Rounded.Storefront; "PYME" -> Icons.Rounded.Apartment; else -> Icons.Rounded.PersonOutline })
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(Display.document(applicant.document, mask), style = MaterialTheme.typography.titleMedium)
                    Text(applicant.activity, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Display.label(applicant.segment ?: "PERSONA"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Desde ${Display.date(applicant.createdAt).substringBefore(" ·")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}
@Composable fun SearchScreen(vm: AppViewModel, onClient: (String) -> Unit) {
    var query by remember { mutableStateOf(vm.searchDocument) }
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { keyboard?.hide(); vm.search(query) }
    PageColumn {
        PageIntro("Un documento, toda su historia", "Encuentra a tu cliente", "Ingresa su CI o NIT completo para consultar su perfil financiero.", Icons.Rounded.PersonSearch)
        OutlinedTextField(query, { query = it; vm.clearSearch() }, label = { Text("Documento de identidad") },
            placeholder = { Text("CI o NIT") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.trim().length >= 3) submit() }))
        Button(onClick = { submit() }, enabled = query.trim().length in 3..64 && !vm.search.loading, modifier = Modifier.fillMaxWidth()) { Text("Buscar cliente") }
        if (vm.search.value == null && !vm.search.loading && vm.search.error == null) BrandPanel {
            Icon(Icons.Rounded.Fingerprint, null, Modifier.size(32.dp), tint = Gold)
            Text("Cada perfil,\ncon su autorización.", style = MaterialTheme.typography.headlineLarge)
            Text("Consulta el historial, las fuentes autorizadas y las evaluaciones de los clientes de tu institución.", style = MaterialTheme.typography.bodyMedium, color = Ivory.copy(alpha = .8f))
        }
        if (vm.search.loading) Loading("Buscando en tu institución…")
        vm.search.error?.let { Notice("No pudimos buscar", it, { submit() }) }
        vm.search.value?.let { result ->
            val applicant = result.items.firstOrNull()
            if (applicant != null) ApplicantCard(applicant, vm.mask) { onClient(applicant.id) }
            else EmptyState("Cliente no registrado", "No hay un cliente con ese documento en tu institución.", "Evaluar como cliente nuevo", { vm.startEvaluation(vm.searchDocument) })
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReviewScreen(vm: AppViewModel, client: (String) -> Unit, resolve: (String, String?) -> Unit) {
    val bottom = LocalPageBottomPadding.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 880.dp).fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = bottom), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { PageIntro("Tu criterio hace la diferencia", "Decisiones pendientes", vm.reviews.value?.let { "${Display.number(it.total)} ${if (it.total == 1) "caso por resolver" else "casos por resolver"} · Más antiguos primero" }, Icons.AutoMirrored.Rounded.FactCheck) }
            item { ChipSelector(listOf("Todos", "Revisión manual", "Solicitar datos", "Parciales"), vm.reviewFilter, { it }) { vm.reviewFilter = it } }
            vm.reviews.error?.let { item { Notice(if (vm.reviews.status == 403) "Tu rol no resuelve revisiones" else "No pudimos cargar la bandeja", it, { vm.refreshReviews() }) } }
            if (vm.reviews.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            vm.reviews.value?.let { page ->
                val visible = page.items.filter { item -> when (vm.reviewFilter) {
                    "Revisión manual" -> item.motive == "REVISION_MANUAL"; "Solicitar datos" -> item.motive == "SOLICITAR_DATOS"; "Parciales" -> item.partial; else -> true
                } }
                if (visible.isEmpty()) item { EmptyState("Sin casos pendientes", "Prueba otro filtro o vuelve a actualizar la bandeja.") }
                items(visible, key = { it.assessmentId }) { item ->
                    val swipe = rememberSwipeToDismissBoxState()
                    LaunchedEffect(item.assessmentId) {
                        snapshotFlow { swipe.currentValue }.filter { it != SwipeToDismissBoxValue.Settled }.collect { direction ->
                            swipe.reset()
                            resolve(item.assessmentId, if (direction == SwipeToDismissBoxValue.StartToEnd) "APROBADO" else "RECHAZADO")
                        }
                    }
                    SwipeToDismissBox(state = swipe, backgroundContent = {
                        Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, "Aprobar", tint = MaterialTheme.colorScheme.primary)
                            Icon(Icons.Rounded.Cancel, "Rechazar", tint = MaterialTheme.colorScheme.error)
                        }
                    }) { ReviewCard(item, vm.mask, { resolve(item.assessmentId, null) }, { client(item.applicantId) }, { resolve(item.assessmentId, it) }) }
                }
                if (page.items.size < page.total) item {
                    vm.reviewPageError?.let { Notice("No pudimos cargar más casos", it) }
                    OutlinedButton(onClick = vm::moreReviews, enabled = !vm.loadingMoreReviews, modifier = Modifier.fillMaxWidth()) { Text(if (vm.loadingMoreReviews) "Cargando…" else "Cargar más casos") }
                }
                item { Text("Aprobar o rechazar requiere una justificación y queda en la auditoría.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
