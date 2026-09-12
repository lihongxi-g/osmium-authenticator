package com.safekey.authenticator.autofill

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.safekey.authenticator.R
import com.safekey.authenticator.security.AppLog

/**
 * Transparent one-shot authentication screen for autofill datasets.
 *
 * Contract (Dataset.Builder#setAuthentication reference): the system launches
 * this activity before filling an authenticated dataset; it must set
 * RESULT_OK once the user authenticates and RESULT_CANCELED otherwise.
 *
 * The prompt rules match MainActivity's (which were battle-tested on ColorOS):
 * BIOMETRIC_STRONG only, a device-credential fallback, negative button text
 * only for biometric-only prompts, and no negative button when
 * DEVICE_CREDENTIAL is allowed.
 */
class AutofillAuthActivity : FragmentActivity() {

    private var finished = false
    private var prompt: BiometricPrompt? = null
    private val timeoutHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this))
        // Default outcome is "denied" — anything that ends this activity
        // without an explicit result cancels the fill.
        setResult(RESULT_CANCELED)
        showPrompt()
        // Safety net: never leave a fill request hanging forever.
        timeoutHandler.postDelayed({
            if (!finished) finishWith(RESULT_CANCELED, "timeout")
        }, 60_000)
    }

    private fun showPrompt() {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                finishWith(RESULT_OK, "ok")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                AppLog.d("autofill auth: error $errorCode: $errString")
                finishWith(RESULT_CANCELED, "error $errorCode")
            }

            override fun onAuthenticationFailed() {
                // Mismatch — the system prompt stays up for another attempt.
                AppLog.d("autofill auth: attempt failed")
            }
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        this.prompt = prompt

        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.autofill_auth_title))
            .setSubtitle(getString(R.string.autofill_auth_subtitle))
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                // A single prompt that allows either biometrics or the device
                // credential; negative button text is forbidden here.
                builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                val strong = BiometricManager.from(this).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
                ) == BiometricManager.BIOMETRIC_SUCCESS
                if (strong) {
                    builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    builder.setNegativeButtonText(getString(R.string.cancel))
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
            }
        } catch (e: Exception) {
            AppLog.d("autofill auth: prompt config failed: ${e.javaClass.simpleName}")
            // The device promised it could authenticate but cannot right now —
            // fail open to match "no auth available" semantics (logged).
            finishWith(RESULT_OK, "config-unavailable")
            return
        }
        try {
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            AppLog.d("autofill auth: authenticate failed: ${e.javaClass.simpleName}: ${e.message}")
            finishWith(RESULT_OK, "prompt-unavailable")
        }
    }

    override fun onStop() {
        super.onStop()
        // On some OEM builds the system prompt dies without any callback when
        // the activity stops — cancel explicitly and close out (same rule the
        // main screen uses).
        if (!finished) {
            try {
                prompt?.cancelAuthentication()
            } catch (_: Exception) {
            }
            finishWith(RESULT_CANCELED, "stopped")
        }
    }

    private fun finishWith(code: Int, reason: String) {
        if (finished) return
        finished = true
        AppLog.d("autofill auth: finish result=$code ($reason)")
        setResult(code)
        finish()
    }

    override fun onDestroy() {
        timeoutHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
