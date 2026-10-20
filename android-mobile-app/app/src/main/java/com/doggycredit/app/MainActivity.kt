package com.doggycredit.app

import android.app.KeyguardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.doggycredit.app.data.Server
import com.doggycredit.app.ui.DoggyCreditApp
import com.doggycredit.app.ui.theme.*
import com.doggycredit.app.ui.components.BrandButton

class MainActivity : FragmentActivity() {
    private val model: AppViewModel by viewModels()
    private val settings by lazy { getSharedPreferences("security", MODE_PRIVATE) }
    private var lockEnabled by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var covered by mutableStateOf(false)
    private var lockMessage by mutableStateOf<String?>(null)
    private var credentialSuccess: (() -> Unit)? = null
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> model.completeSignIn(result.data) }
    private val credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) credentialSuccess?.invoke()
        credentialSuccess = null
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lockEnabled = settings.getBoolean("lock.enabled", false)
        locked = lockEnabled
        setContent {
            DoggyCreditTheme {
                val authenticated = model.user != null
                val dark = isSystemInDarkTheme()
                SideEffect {
                    val brandSurface = !authenticated || locked || covered
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !brandSurface && !dark
                        isAppearanceLightNavigationBars = !brandSurface && !dark
                    }
                    if (authenticated) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                Surface(Modifier.fillMaxSize()) {
                    val protected = authenticated && (locked || covered)
                    ProtectedSession(protected = protected, onLeave = { moveTaskToBack(true) }, cover = {
                        LockScreen(
                            unlock = if (covered) null else { { authenticate { locked = false; lockMessage = null } } },
                            message = lockMessage,
                            signOut = if (covered) null else { { locked = false; model.signOut() } }
                        )
                    }) {
                        DoggyCreditApp(model, signIn = {
                            try { authLauncher.launch(model.auth.authorizationIntent()) }
                            catch (_: Exception) { model.notice = "No se encontró un navegador compatible. Instala o habilita un navegador para iniciar sesión." }
                        }, lockEnabled, canAuthenticate(), toggleLock = { enabled ->
                            authenticate {
                                lockEnabled = enabled
                                settings.edit().putBoolean("lock.enabled", enabled).apply()
                                locked = false
                            }
                        }, openConsole = { plans -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Server.CONSOLE + if (plans) "/configuracion" else ""))) }, privacyProtected = protected)
                    }
                }
            }
        }
    }
    private fun canAuthenticate(): Boolean {
        val manager = BiometricManager.from(this)
        val authenticators = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS || getSystemService(KeyguardManager::class.java).isDeviceSecure
    }
    private fun authenticate(success: () -> Unit) {
        val authenticators = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { success() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { lockMessage = errString.toString() }
            })
            val info = BiometricPrompt.PromptInfo.Builder().setTitle("Desbloquear DoggyCredit").setSubtitle("Protege los datos de tus clientes").setAllowedAuthenticators(authenticators)
            if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Cancelar")
            prompt.authenticate(info.build())
        } else {
            @Suppress("DEPRECATION") val intent = getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent("DoggyCredit", "Confirma el bloqueo de tu dispositivo")
            if (intent != null) { credentialSuccess = success; credentialLauncher.launch(intent) }
            else lockMessage = "Configura un bloqueo de pantalla en los ajustes del dispositivo."
        }
    }
    override fun onPause() { covered = true; super.onPause() }
    override fun onResume() { super.onResume(); covered = false }
    override fun onStop() {
        if (!isChangingConfigurations && model.user != null && lockEnabled) locked = true
        super.onStop()
    }
}
/** La salida protegida abandona la tarea sin revelar ni desmontar el contenido. */
@Composable internal fun ProtectedSession(protected: Boolean, onLeave: () -> Unit,
                                         cover: @Composable () -> Unit, content: @Composable () -> Unit) {
    val focus = LocalFocusManager.current
    LaunchedEffect(protected) { if (protected) focus.clearFocus(force = true) }
    Box(Modifier.fillMaxSize()) {
        Box(if (protected) Modifier.clearAndSetSemantics { } else Modifier) { content() }
        if (protected) cover()
    }
    // Se registra al entrar en la portada, después del NavHost y sus manejadores.
    // Mantenerlo instalado antes del contenido permite que la navegación cubierta lo preceda.
    if (protected) BackHandler(onBack = onLeave)
}
@Composable internal fun LockScreen(unlock: (() -> Unit)?, message: String?, signOut: (() -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Pine).pointerInput(Unit) { detectTapGestures(onTap = {}) }.safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.brand_horizontal_inverted), "DoggyCredit", Modifier.fillMaxWidth().height(90.dp))
            Spacer(Modifier.height(32.dp))
            if (unlock != null) {
                Text("Tu información está protegida", color = Ivory, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                BrandButton("Desbloquear", unlock)
                message?.let { Text(it, color = Ivory, modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) }
                signOut?.let { TextButton(onClick = it) { Text("Cerrar sesión", color = Ivory) } }
            }
        }
    }
}
