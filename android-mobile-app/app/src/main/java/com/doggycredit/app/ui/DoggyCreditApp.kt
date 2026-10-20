package com.doggycredit.app.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import com.doggycredit.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.model.Display
import com.doggycredit.app.ui.components.*
import com.doggycredit.app.ui.screens.*
import com.doggycredit.app.ui.theme.*
import kotlinx.coroutines.launch

data class Destination(val route: String, val label: String, val icon: ImageVector)
private val destinations = listOf(
    Destination("home", "Inicio", Icons.Rounded.Home), Destination("reviews", "Revisión", Icons.AutoMirrored.Rounded.Rule),
    Destination("clients", "Clientes", Icons.Rounded.People), Destination("analytics", "Analítica", Icons.Rounded.BarChart),
    Destination("search", "Buscar", Icons.Rounded.Search)
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun DoggyCreditApp(vm: AppViewModel, signIn: () -> Unit, lockEnabled: Boolean, lockAvailable: Boolean,
                             toggleLock: (Boolean) -> Unit, openConsole: (Boolean) -> Unit, privacyProtected: Boolean = false) {
    if (vm.restoring) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Loading("Restaurando sesión…") }; return }
    if (vm.user == null || vm.signingIn) {
        WelcomeScreen(vm.signingIn, vm.notice, signIn) { vm.notice = null }
        return
    }
    // La navegación y sus ViewModels pertenecen únicamente a la sesión actual.
    CompositionLocalProvider(LocalPrivacyProtected provides privacyProtected) {
    key(vm.user?.tenantId, vm.user?.username) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: "home"
        val isRoot = destinations.any { it.route == route }
        var selected by rememberSaveable { mutableStateOf("home") }
        fun navigateTab(target: String) {
            selected = target
            nav.navigate(target) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true; restoreState = true
            }
        }
        fun client(id: String) { nav.navigate("client/${Uri.encode(id)}") }
        fun assessment(id: String) { nav.navigate("assessment/${Uri.encode(id)}") }
        fun resolve(id: String, preset: String?) { nav.navigate("resolve/${Uri.encode(id)}?preset=${preset.orEmpty()}") }
        val colors = MaterialTheme.colorScheme
        val darkTheme = colors.surface.luminance() < .5f
        val largeText = LocalDensity.current.fontScale >= 1.4f
        val drawer = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        LaunchedEffect(privacyProtected, largeText) { if (privacyProtected || !largeText) drawer.close() }
        val itemColors = NavigationSuiteDefaults.itemColors(
            navigationBarItemColors = NavigationBarItemDefaults.colors(selectedIconColor = colors.onPrimary,
                selectedTextColor = colors.primary, indicatorColor = colors.primary,
                unselectedIconColor = colors.onSurfaceVariant, unselectedTextColor = colors.onSurfaceVariant),
            navigationRailItemColors = NavigationRailItemDefaults.colors(selectedIconColor = colors.onPrimary,
                selectedTextColor = colors.primary, indicatorColor = colors.primary))
        val title = destinations.firstOrNull { it.route == route }?.label ?: when {
            route.startsWith("client/") -> "Perfil del cliente"; route.startsWith("assessment/") -> "Evaluación"
            route.startsWith("resolve/") -> "Resolver caso"; else -> "Cuenta"
        }
        CompositionLocalProvider(LocalPageBottomPadding provides if (isRoot && !largeText) 112.dp else 28.dp) {
        ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = largeText && !privacyProtected, drawerContent = {
            if (largeText && !privacyProtected) ModalDrawerSheet(Modifier.widthIn(max = 360.dp).fillMaxWidth(.9f)) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Navegación", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 16.dp))
                        IconButton(onClick = { scope.launch { drawer.close() } }) { Icon(Icons.Rounded.Close, "Cerrar navegación") }
                    }
                    destinations.forEach { destination ->
                        NavigationDrawerItem(selected = (if (isRoot) route else selected) == destination.route,
                            onClick = { navigateTab(destination.route); scope.launch { drawer.close() } },
                            icon = { Icon(destination.icon, null) }, label = { Text(destination.label) },
                            badge = if (destination.route == "reviews" && (vm.reviews.value?.total ?: 0) > 0) {
                                { Text("${vm.reviews.value?.total}") }
                            } else null)
                    }
                }
            }
        }) {
        NavigationSuiteScaffold(containerColor = colors.background,
            layoutType = if (largeText) NavigationSuiteType.None else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
            navigationSuiteColors = NavigationSuiteDefaults.colors(navigationBarContainerColor = colors.surface,
                navigationRailContainerColor = colors.surface), navigationSuiteItems = {
            destinations.forEach { destination ->
                item(selected = (if (isRoot) route else selected) == destination.route, onClick = { navigateTab(destination.route) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) }, colors = itemColors,
                    badge = if (destination.route == "reviews" && (vm.reviews.value?.total ?: 0) > 0) {
                        { Badge(containerColor = Gold, contentColor = Pine) { Text("${vm.reviews.value?.total}") } }
                    } else null)
            }
        }) {
            Scaffold(containerColor = colors.background, topBar = {
                TopAppBar(title = {
                    if (route == "home") Image(painterResource(if (darkTheme) R.drawable.brand_horizontal_inverted else R.drawable.brand_horizontal), "DoggyCredit", Modifier.width(154.dp).height(44.dp))
                    else Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }, navigationIcon = {
                    if (!isRoot) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") }
                    else if (largeText) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Abrir navegación") }
                }, actions = {
                    if (largeText && !isRoot) IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Rounded.Menu, "Abrir navegación") }
                    if (isRoot && route != "search") IconButton(onClick = {
                        when (route) { "clients" -> vm.refreshClients(); "reviews" -> vm.refreshReviews(); "analytics" -> vm.refreshAnalytics(); else -> vm.refreshSession() }
                    }) { Icon(Icons.Rounded.Refresh, "Actualizar") }
                    if (isRoot) Surface(onClick = { nav.navigate("account") { launchSingleTop = true } },
                        modifier = Modifier.padding(start = 8.dp, end = 16.dp).size(48.dp).semantics { contentDescription = "Cuenta de ${vm.user?.name}" },
                        shape = CircleShape, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer,
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = .15f))) {
                        Box(contentAlignment = Alignment.Center) { Text(vm.user?.initials.orEmpty(), style = MaterialTheme.typography.labelLarge) }
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background))
            }, floatingActionButton = {
                if (isRoot && !largeText) ExtendedFloatingActionButton(modifier = Modifier.semantics { contentDescription = "Nueva evaluación" },
                    onClick = { vm.startEvaluation() }, containerColor = Pine, contentColor = Ivory,
                    shape = RoundedCornerShape(20.dp), elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
                    icon = { Box(Modifier.size(28.dp).background(Gold, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Pine)
                    } }, text = { Text("Evaluar", fontWeight = FontWeight.SemiBold) })
            }, bottomBar = {
                if (largeText && (isRoot || (vm.draft.inProgress && !vm.showEvaluation))) Surface(color = colors.surface) {
                    Row(Modifier.fillMaxWidth().testTag("large-text-actions").padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (vm.draft.inProgress && !vm.showEvaluation) TextButton(onClick = vm::resumeEvaluation,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics {
                                contentDescription = "Retomar evaluación. Paso ${vm.draft.step + 1} de 4"
                            }) { Text("Retomar") }
                        if (isRoot) Button(onClick = { vm.startEvaluation() },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "Nueva evaluación" }) {
                            Text("Evaluar")
                        }
                    }
                } else if (vm.draft.inProgress && !vm.showEvaluation) Surface(color = colors.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.Assignment, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("Evaluación en curso", style = MaterialTheme.typography.labelLarge)
                            Text("Paso ${vm.draft.step + 1} de 4 · En esta sesión", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = vm::resumeEvaluation) { Text("Retomar") }
                    }
                }
            }) { padding ->
                NavHost(navController = nav, startDestination = "home", modifier = Modifier.fillMaxSize().padding(padding)) {
                    composable("home") { Refreshable(vm.home.loading, vm::refreshSession) { HomeScreen(vm, ::client, ::resolve, { navigateTab("reviews") }) } }
                    composable("clients") { Refreshable(vm.clients.loading, { vm.refreshClients() }) { ClientsScreen(vm, ::client) } }
                    composable("reviews") { Refreshable(vm.reviews.loading, { vm.refreshReviews() }) { ReviewScreen(vm, ::client, ::resolve) } }
                    composable("analytics") { Refreshable(vm.analytics.loading, { vm.refreshAnalytics() }) { AnalyticsScreen(vm) { openConsole(true) } } }
                    composable("search") { SearchScreen(vm, ::client) }
                    composable("account") { AccountScreen(vm, lockEnabled, lockAvailable, toggleLock, { openConsole(false) }) }
                    composable("client/{id}") { entry -> ClientScreen(entry.arguments!!.getString("id")!!, vm, ::assessment) }
                    composable("assessment/{id}") { entry -> AssessmentScreen(entry.arguments!!.getString("id")!!, vm) }
                    composable("resolve/{id}?preset={preset}", arguments = listOf(navArgument("preset") { defaultValue = "" })) { entry ->
                        ResolveScreen(entry.arguments!!.getString("id")!!, entry.arguments?.getString("preset"), vm, ::client, ::assessment) { nav.popBackStack() }
                    }
                }
            }
        }
        }
        }
        if (vm.showEvaluation && !privacyProtected) EvaluationScreen(vm, ::client)
        vm.pendingEvaluation?.takeUnless { privacyProtected }?.let { request ->
            AlertDialog(onDismissRequest = vm::cancelNewEvaluation,
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
                title = { Text("Tienes una evaluación en curso") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (vm.draft.document.isBlank()) "Hay un borrador sin documento de cliente."
                            else "El borrador corresponde a ${Display.document(vm.draft.document, vm.mask)}.")
                        request.document?.let { Text("Quieres iniciar una evaluación para ${Display.document(it, vm.mask)}.") }
                        Text("Puedes retomarlo o descartarlo para iniciar otra evaluación. Lo que ya se registró se conserva en el servidor.")
                        if (vm.draft.busy) Text("Hay una operación en curso. Espera a que termine antes de descartar el borrador.")
                    }
                },
                confirmButton = {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::resumeEvaluation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Retomar evaluación") }
                        OutlinedButton(onClick = vm::confirmNewEvaluation, enabled = !vm.draft.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Descartar y crear nueva") }
                        TextButton(onClick = vm::cancelNewEvaluation, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Cancelar") }
                    }
                })
        }
    }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Refreshable(loading: Boolean, refresh: () -> Unit, content: @Composable () -> Unit) {
    PullToRefreshBox(isRefreshing = loading, onRefresh = refresh, modifier = Modifier.fillMaxSize()) { content() }
}
