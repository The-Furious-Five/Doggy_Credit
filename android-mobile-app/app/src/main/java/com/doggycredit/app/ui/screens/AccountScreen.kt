package com.doggycredit.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.doggycredit.app.AppViewModel
import com.doggycredit.app.BuildConfig
import com.doggycredit.app.model.Display
import com.doggycredit.app.ui.components.*
import com.doggycredit.app.ui.theme.*

@Composable fun AccountScreen(vm: AppViewModel, lockEnabled: Boolean, lockAvailable: Boolean, toggleLock: (Boolean) -> Unit, openConsole: () -> Unit) {
    var confirmSignOut by remember { mutableStateOf(false) }
    PageColumn {
        vm.user?.let { user ->
            BrandPanel {
                Surface(shape = CircleShape, color = Gold, contentColor = Pine) {
                    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) { Text(user.initials, style = MaterialTheme.typography.titleLarge) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("Tu espacio en DoggyCredit", color = Gold)
                    Text(user.name, style = MaterialTheme.typography.headlineLarge)
                    Text(user.roles.joinToString(" · ") { Display.label(it) }.ifBlank { "Sin rol asignado" }, style = MaterialTheme.typography.bodyMedium, color = Ivory.copy(alpha = .8f))
                    if (user.username.isNotBlank()) Text(user.username, style = MaterialTheme.typography.bodySmall, color = Ivory.copy(alpha = .7f))
                }
            }
        }
        SectionTitle("Tu institución")
        RemoteContent(vm.institution, vm::refreshSession) { tenant ->
            Card { Column(Modifier.fillMaxWidth().padding(16.dp)) { DataRow("Nombre", tenant.name); DataRow("Plan", Display.label(tenant.plan)) } }
        }
        SectionTitle("Uso del plan")
        RemoteContent(vm.usage, vm::refreshSession) { usage ->
            UsageOverview(usage, vm.institution.value?.plan)
            if (usage.warning) Notice("Uso de tu plan", if (usage.exhausted) "Llegaste al límite mensual del plan." else "Ya utilizaste al menos el 80 % de la cuota.")
        }
        SectionTitle("Seguridad y privacidad")
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Icons.Rounded.VerifiedUser)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Bloquear la aplicación", style = MaterialTheme.typography.titleSmall); Text("Huella, rostro o bloqueo del dispositivo", style = MaterialTheme.typography.bodySmall) }
                    Switch(lockEnabled, onCheckedChange = toggleLock, enabled = lockAvailable)
                }
                if (!lockAvailable) Text("Configura un bloqueo de pantalla en tu dispositivo para activar esta opción.", style = MaterialTheme.typography.bodySmall)
                DataRow("Documentos y montos", if (vm.mask) "Enmascarados" else "Visibles para tu rol")
                Text("El contenido de clientes se protege en el selector de aplicaciones y no se permite capturarlo.", style = MaterialTheme.typography.bodySmall)
            }
        }
        vm.preferences.error?.let { Notice("Preferencias de privacidad no disponibles", it, vm::refreshSession) }
        SectionTitle("Consola web")
        Text("Productos, políticas, integraciones, claves de API, webhooks y equipo se administran desde la consola.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = openConsole, modifier = Modifier.fillMaxWidth()) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Abrir consola web") }
        DataRow("Servidor", "doggycredit.los5furiosos.cloud")
        DataRow("Versión", BuildConfig.VERSION_NAME)
        Button(onClick = { confirmSignOut = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Cerrar sesión") }
    }
    if (confirmSignOut && !LocalPrivacyProtected.current) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text("¿Cerrar sesión?") },
        text = { Text(if (vm.draft.inProgress) "Se descartará el borrador de evaluación. Los registros ya enviados se conservan en el servidor." else "Para volver a entrar tendrás que iniciar sesión con tu institución.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } })
}
