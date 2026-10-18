package com.doggycredit.app

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.doggycredit.app.data.Server
import net.openid.appauth.AuthorizationManagementActivity
import net.openid.appauth.AuthorizationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Ejercita el callback real de AppAuth sin credenciales ni una sesión de prueba. */
class AuthCallbackTests {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun nativeCallbackDisplaysAuthorizationErrorAndAllowsRetry() {
        returnFromBrowser(mismatchedState = false)
        compose.onNodeWithText("Acceso cancelado por el usuario.").assertIsDisplayed()
        dismissErrorAndCheckWelcome()
    }

    @Test fun nativeCallbackRejectsMismatchedStateWithoutOpeningSession() {
        returnFromBrowser(mismatchedState = true)
        dismissErrorAndCheckWelcome()
    }

    private fun returnFromBrowser(mismatchedState: Boolean) {
        val app = compose.activity.application as DoggyCreditApplication
        val request = AtomicReference<AuthorizationRequest>()
        val browserOpened = CountDownLatch(1)
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is AuthorizationManagementActivity) {
                    // La misma solicitud serializada que AppAuth guarda entre actividades.
                    activity.intent.getStringExtra("authRequest")?.let {
                        request.set(AuthorizationRequest.jsonDeserialize(it))
                    }
                }
            }
            override fun onActivityStopped(activity: Activity) {
                if (activity is AuthorizationManagementActivity) browserOpened.countDown()
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Iniciar sesión con tu institución").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Iniciar sesión con tu institución").performClick()
            assertTrue("El navegador debe abrirse antes del callback", browserOpened.await(15, TimeUnit.SECONDS))
            val authorization = requireNotNull(request.get())
            assertEquals(Server.CLIENT, authorization.clientId)
            assertEquals(Server.REDIRECT, authorization.redirectUri.toString())
            assertEquals("S256", authorization.codeVerifierChallengeMethod)
            assertNotNull(authorization.codeVerifier)
            val callback = authorization.redirectUri.buildUpon()
                .appendQueryParameter("state", if (mismatchedState) "state-no-coincidente" else authorization.state)
            if (mismatchedState) callback.appendQueryParameter("code", "unused-invalid-code")
            else callback.appendQueryParameter("error", "access_denied")
                .appendQueryParameter("error_description", "Acceso cancelado por el usuario.")
            val intent = Intent(Intent.ACTION_VIEW, callback.build())
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            InstrumentationRegistry.getInstrumentation().targetContext.startActivity(intent)
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("No pudimos continuar").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("No pudimos continuar").assertIsDisplayed()
            assertNull(app.auth.user.value)
        } finally {
            app.unregisterActivityLifecycleCallbacks(callbacks)
        }
    }

    private fun dismissErrorAndCheckWelcome() {
        compose.onNodeWithText("Entendido").performClick()
        compose.onNodeWithText("Iniciar sesión con tu institución").assertIsDisplayed().assertIsEnabled()
    }
}
