package com.doggycredit.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.doggycredit.app.R
import com.doggycredit.app.ui.theme.*

@Composable fun WelcomeScreen(busy: Boolean, error: String?, signIn: () -> Unit, clearError: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Pine).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                Image(painterResource(R.drawable.brand_horizontal_inverted), "DoggyCredit", Modifier.fillMaxWidth(.85f).height(86.dp))
                Text("Tu oficina de crédito,\nen el bolsillo.", color = Ivory, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Evalúa a tus clientes donde estén y resuelve los casos en revisión entre visitas.", color = Ivory.copy(alpha = .8f), style = MaterialTheme.typography.bodyLarge)
                WelcomeFeature(Icons.AutoMirrored.Rounded.Assignment, "Evaluación en campo", "Consentimiento, fuentes y score con sus razones.")
                WelcomeFeature(Icons.AutoMirrored.Rounded.Rule, "Bandeja de revisión", "Aprueba o rechaza con justificación.")
                WelcomeFeature(Icons.Rounded.AccountBalance, "Perfil Financiero 360", "Buró, banco y billetera en una sola vista.")
            }
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = signIn, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Ivory, contentColor = Pine)) {
                    if (busy) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.width(12.dp)) }
                    Text(if (busy) "Conectando con tu institución…" else "Iniciar sesión con tu institución")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = Ivory.copy(alpha = .7f))
                    Text("doggycredit.los5furiosos.cloud", color = Ivory.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    error?.let { AlertDialog(onDismissRequest = clearError, title = { Text("No pudimos continuar") }, text = { Text(it) }, confirmButton = { TextButton(onClick = clearError) { Text("Entendido") } }) }
}
@Composable private fun WelcomeFeature(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(28.dp), tint = Honey)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Ivory, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = Ivory.copy(alpha = .75f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
