package com.safekey.authenticator

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.data.LanguagePrefs
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.IntegrityCheck
import com.safekey.authenticator.totp.OtpUriParser
import com.safekey.authenticator.ui.components.SwipeBackContainer
import com.safekey.authenticator.ui.navigation.Screen
import com.safekey.authenticator.ui.screens.AccountFormScreen
import com.safekey.authenticator.ui.screens.AccountsScreen
import com.safekey.authenticator.ui.screens.DetailScreen
import com.safekey.authenticator.ui.screens.ExportScreen
import com.safekey.authenticator.ui.screens.ImportScreen
import com.safekey.authenticator.ui.screens.LockScreen
import com.safekey.authenticator.ui.screens.PinSetupScreen
import com.safekey.authenticator.ui.screens.PinVerifyScreen
import com.safekey.authenticator.ui.screens.ScanScreen
import com.safekey.authenticator.ui.screens.SettingsScreen
import com.safekey.authenticator.ui.theme.SafeKeyTheme
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()
    private var tampered = false

    // ------------------------------------------------------------ lifecycle

    override fun attachBaseContext(newBase: Context) {
        val lang = LanguagePrefs.get(newBase)
        if (lang != null) {
            val locale = Locale.forLanguageTag(lang)
            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocales(android.os.LocaleList(locale))
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Anti-repackaging: refuse to run a re-signed APK.
        tampered = IntegrityCheck.isTampered(this)

        // Block screenshots and the recents thumbnail — no secrets leak.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        setContent {
            val settings by vm.settings.collectAsState()
            val locked by vm.locked.collectAsState()
            val pinRequired by vm.pinRequired.collectAsState()
            val destroyed by vm.destroyed.collectAsState()
            val toast by vm.toast.collectAsState()
            val context = LocalContext.current

            SafeKeyTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                // The window background follows the app theme (not just the
                // system dark mode) — otherwise dark theme pages flash white
                // during transitions and gates show white-on-white text.
                val view = LocalView.current
                if (!view.isInEditMode) {
                    val background = MaterialTheme.colorScheme.background
                    SideEffect {
                        view.setBackgroundColor(background.toArgb())
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when {
                        tampered -> TamperedScreen()
                        destroyed -> DestroyedScreen()
                        pinRequired -> PinGate()
                        locked -> LockGate()
                        else -> MainNavHost()
                    }
                }
            }

            LaunchedEffect(toast) {
                toast?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    vm.clearToast()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setBiometricAvailable(canAuthenticateBiometric())
        vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onAppBackground()
    }

    // ------------------------------------------------------------ tamper

    @Composable
    private fun TamperedScreen() {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(stringResource(R.string.tampered_title)) },
            text = { Text(stringResource(R.string.tampered_message)) },
            confirmButton = {
                TextButton(onClick = { finishAffinity() }) {
                    Text(stringResource(R.string.tampered_exit))
                }
            }
        )
    }

    // ------------------------------------------------------------ PIN gate

    @Composable
    private fun PinGate() {
        val error by vm.pinError.collectAsState()
        val attempts = vm.remainingAttempts()

        PinVerifyScreen(
            title = stringResource(R.string.pin_verify_title),
            subtitle = stringResource(R.string.pin_verify_subtitle),
            error = error,
            remainingAttempts = attempts,
            onVerify = { pin ->
                if (!vm.onPinEntered(pin)) {
                    // Wrong PIN — if a self-destruct PIN is armed, entering it
                    // here destroys all data.
                    if (vm.settings.value.destroyMode == AppSettings.DESTROY_PIN &&
                        vm.pinManager.hasDestroyPin()
                    ) {
                        if (vm.onSelfDestructPinEntered(pin)) {
                            vm.selfDestruct()
                        }
                    }
                }
            },
            onCancel = null // periodic verification cannot be skipped while required
        )
    }

    @Composable
    private fun DestroyedScreen() {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.destroyed_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.destroyed_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // ------------------------------------------------------------ lock gate

    @Composable
    private fun LockGate() {
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var pinMode by remember { mutableStateOf(false) }
        val context = LocalContext.current

        if (pinMode) {
            // Osmium PIN entry inside the biometric gate — correct PIN passes,
            // and the self-destruct PIN (if armed) still triggers destruction.
            val attempts = vm.remainingAttempts()
            PinVerifyScreen(
                title = stringResource(R.string.pin_verify_title),
                subtitle = stringResource(R.string.pin_verify_subtitle),
                error = errorMessage,
                remainingAttempts = attempts,
                onVerify = { pin ->
                    if (!vm.onPinEntered(pin)) {
                        if (vm.settings.value.destroyMode == AppSettings.DESTROY_PIN &&
                            vm.pinManager.hasDestroyPin()
                        ) {
                            if (vm.onSelfDestructPinEntered(pin)) {
                                vm.selfDestruct()
                            }
                        }
                    }
                },
                onCancel = {
                    pinMode = false
                    errorMessage = null
                }
            )
        } else {
            LockScreen(
                errorMessage = errorMessage,
                onUnlock = {
                    errorMessage = null
                    if (canAuthenticateBiometric()) {
                        launchBiometric(
                            onSuccess = { vm.unlock() },
                            onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                            onError = { msg -> errorMessage = msg }
                        )
                    } else {
                        errorMessage = context.getString(R.string.biometric_unavailable)
                    }
                },
                onUsePassword = {
                    errorMessage = null
                    launchCredential(
                        onSuccess = { vm.unlock() },
                        onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                        onError = { msg -> errorMessage = msg }
                    )
                },
                onUsePin = {
                    errorMessage = null
                    if (vm.hasLocalPin()) {
                        pinMode = true
                    } else {
                        errorMessage = context.getString(R.string.pin_not_set)
                    }
                }
            )

            // Attempt unlock automatically once the gate appears (after the
            // activity is fully resumed — avoids prompt-vs-window races).
            LaunchedEffect(Unit) {
                delay(400)
                if (canAuthenticateBiometric() && vm.locked.value) {
                    launchBiometric(
                        onSuccess = { vm.unlock() },
                        onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                        onError = { msg -> errorMessage = msg }
                    )
                }
            }
        }
    }

    private fun canAuthenticateBiometric(): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            // androidx fallback path (FingerprintManager) for Android 8/9
            BiometricManager.from(this).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        }

    private fun canAuthenticateAny(): Boolean =
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            canAuthenticateBiometric() || isDeviceSecure()
        }

    private fun isDeviceSecure(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    // ------------------------------------------------------- biometric core

    private var promptInFlight = false
    private var promptSeq = 0
    private val promptTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Fingerprint / face prompt (BIOMETRIC_STRONG only).
     *
     * Two Android 14+ rules that older androidx.biometric versions violate
     * (and why the prompt kept failing on ColorOS 15):
     * 1. NEVER call setNegativeButtonText when DEVICE_CREDENTIAL is allowed.
     * 2. Separate prompts per authenticator type — credential fallback gets
     *    its own prompt (launchCredential below).
     */
    private fun launchBiometric(
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit
    ) = launchPrompt(
        authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG,
        onSuccess = onSuccess,
        onCancelled = onCancelled,
        onError = onError
    )

    /** Lock-screen PIN / password / pattern prompt (DEVICE_CREDENTIAL). */
    private fun launchCredential(
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit
    ) = launchPrompt(
        authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        onSuccess = onSuccess,
        onCancelled = onCancelled,
        onError = onError
    )

    /**
     * Official androidx.biometric rules (PromptInfo.Builder reference):
     * - setNegativeButtonText is REQUIRED for biometric-only prompts; the
     *   library throws IllegalArgumentException("Negative text must be set and
     *   non-empty") otherwise.
     * - setNegativeButtonText is FORBIDDEN when DEVICE_CREDENTIAL is allowed.
     * - DEVICE_CREDENTIAL alone is unsupported before API 30 (use
     *   setDeviceCredentialAllowed there instead).
     */
    private fun launchPrompt(
        authenticators: Int,
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (promptInFlight) {
            AppLog.d("prompt skipped: another prompt in flight")
            return
        }
        promptInFlight = true
        val seq = ++promptSeq
        // Safety net: if the system prompt hangs without any callback the
        // guard would block every future attempt — force-reset after 30s.
        promptTimeoutHandler.postDelayed({
            if (promptSeq == seq && promptInFlight) {
                promptInFlight = false
                restoreSecureFlag()
                AppLog.d("prompt timeout — guard reset")
            }
        }, 30_000)
        try {
            // A window with FLAG_SECURE can block the system prompt on some
            // Android 14+ builds — clear it for the prompt's lifetime.
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            AppLog.d("launch prompt: authenticators=$authenticators")
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptSeq++
                    promptInFlight = false
                    restoreSecureFlag()
                    AppLog.d("prompt SUCCESS type=${result.authenticationType}")
                    vm.onBiometricSucceeded()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptSeq++
                    promptInFlight = false
                    restoreSecureFlag()
                    AppLog.d("prompt ERROR $errorCode: $errString")
                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancelled()
                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    // Counts toward the system lockout and the self-destruct
                    // fail counter; UI stays on the gate.
                    AppLog.d("prompt FAILED (mismatch)")
                    vm.onBiometricFailed()
                }
            }
            // ALWAYS create a fresh prompt instance: BiometricPrompt binds its
            // callback at construction time, so reusing one instance would
            // keep calling the FIRST launch's callbacks forever (the
            // "tap does nothing" bug).
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                callback
            )

            val builder = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            if (authenticators == BiometricManager.Authenticators.DEVICE_CREDENTIAL) {
                if (Build.VERSION.SDK_INT >= 30) {
                    builder.setAllowedAuthenticators(authenticators)
                } else {
                    // Pre-Android 11 path for lock-screen credential
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
            } else {
                builder.setAllowedAuthenticators(authenticators)
                // Required for biometric-only prompts per the reference docs.
                builder.setNegativeButtonText(getString(R.string.cancel))
            }
            prompt.authenticate(builder.build())
        } catch (e: Exception) {
            promptSeq++
            promptInFlight = false
            restoreSecureFlag()
            AppLog.d("prompt EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
            onError(e.message ?: "Biometric error")
        }
    }

    private fun restoreSecureFlag() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }

    // ------------------------------------------------------------ nav host

    @Composable
    private fun CurrentPinVerifyRoute(next: String) {
        var error by remember { mutableStateOf<String?>(null) }
        PinVerifyScreen(
            title = stringResource(R.string.pin_verify_title),
            subtitle = stringResource(R.string.pin_current_hint),
            error = error,
            remainingAttempts = null,
            onVerify = { pin ->
                if (vm.verifyLocalPin(pin)) {
                    vm.nav.pop()
                    when (next) {
                        "change_pin" -> vm.nav.push(Screen.PinSetup("pin"))
                        "clear_pin" -> {
                            vm.clearAppPin()
                            vm.showToast(getString(R.string.pin_cleared))
                        }
                        "clear_destroy_pin" -> {
                            vm.clearDestroyPin()
                            vm.showToast(getString(R.string.destroy_pin_cleared))
                        }
                        "set_destroy_pin", "change_destroy_pin" ->
                            vm.nav.push(Screen.PinSetup("destroy_pin"))
                    }
                } else {
                    error = getString(R.string.pin_wrong)
                }
            },
            onCancel = { vm.nav.pop() }
        )
    }

    @Composable
    private fun MainNavHost() {
        val context = LocalContext.current
        val direction = vm.nav.direction
        val current = vm.nav.current

        BackHandler(enabled = vm.nav.canGoBack) { vm.nav.pop() }

        SwipeBackContainer(
            canGoBack = vm.nav.canGoBack,
            onBack = { vm.nav.pop() }
        ) {
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    if (direction > 0) {
                        (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(200)))
                    }
                },
                label = "nav"
            ) { screen ->
                when (screen) {
                    is Screen.Accounts -> AccountsScreen(
                        vm = vm,
                        onAddScan = { vm.nav.push(Screen.Scan) },
                        onAddManual = { vm.nav.push(Screen.AccountForm(accountId = null, prefill = null)) },
                        onAddPaste = {
                            val clipboard = getSystemService(ClipboardManager::class.java)
                            val clipText = clipboard?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(this@MainActivity)
                                ?.toString()
                                .orEmpty()
                            val parsed = try {
                                OtpUriParser.parse(clipText)
                            } catch (_: Exception) {
                                null
                            }
                            if (parsed != null) {
                                vm.nav.push(Screen.AccountForm(accountId = null, prefill = parsed))
                            } else {
                                vm.showToast(context.getString(R.string.error_uri_invalid))
                            }
                        },
                        onOpenDetail = { account -> vm.nav.push(Screen.Detail(account.id)) },
                        onOpenSettings = { vm.nav.push(Screen.Settings) }
                    )

                    is Screen.AccountForm -> AccountFormScreen(
                        vm = vm,
                        accountId = screen.accountId,
                        prefillUri = screen.prefill,
                        onDone = { vm.nav.pop() },
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.Detail -> DetailScreen(
                        vm = vm,
                        accountId = screen.accountId,
                        onEdit = { account -> vm.nav.push(Screen.AccountForm(account.id)) },
                        onDeleted = { vm.nav.popToRoot() },
                        onBack = { vm.nav.pop() },
                        // null when no biometrics on device — the option
                        // simply doesn't show in the verification dialog
                        onRequireBiometric = if (canAuthenticateBiometric()) {
                            { onSuccess ->
                                launchBiometric(
                                    onSuccess = onSuccess,
                                    onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                    onError = { msg -> vm.showToast(msg) }
                                )
                            }
                        } else null,
                        onRequireCredential = { onSuccess ->
                            launchCredential(
                                onSuccess = onSuccess,
                                onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                onError = { msg -> vm.showToast(msg) }
                            )
                        }
                    )

                    is Screen.Scan -> ScanScreen(
                        vm = vm,
                        onSaved = { vm.nav.pop() },
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.Settings -> SettingsScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
                        onExport = { vm.nav.push(Screen.Export) },
                        onImport = { vm.nav.push(Screen.Import) },
                        onOpenPinSetup = { vm.nav.push(Screen.PinSetup("pin")) },
                        onOpenPinVerify = { next -> vm.nav.push(Screen.PinVerify(next)) },
                        onRequireBiometric = { onSuccess ->
                            // Toggling the gate requires identity verification.
                            if (canAuthenticateBiometric()) {
                                launchBiometric(
                                    onSuccess = onSuccess,
                                    onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                    onError = { msg -> vm.showToast(msg) }
                                )
                            } else {
                                // No biometrics enrolled — nothing to verify with
                                AppLog.d("gate toggle without biometrics on device")
                                onSuccess()
                            }
                        },
                        onLanguageChanged = { lang ->
                            LanguagePrefs.set(this@MainActivity, lang)
                            recreate()
                        }
                    )

                    is Screen.Export -> ExportScreen(
                        vm = vm,
                        onDone = { vm.nav.pop() },
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.Import -> ImportScreen(
                        vm = vm,
                        onDone = { vm.nav.pop() },
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.PinSetup -> PinSetupScreen(
                        title = if (screen.mode == "destroy_pin")
                            stringResource(R.string.destroy_pin_title)
                        else stringResource(R.string.pin_setup_title),
                        description = if (screen.mode == "destroy_pin")
                            stringResource(R.string.destroy_pin_desc)
                        else stringResource(R.string.pin_setup_desc),
                        onValidate = if (screen.mode == "destroy_pin") {
                            // the destruct PIN must differ from the app PIN —
                            // otherwise any normal unlock could trigger self-destruct
                            { pin -> !(vm.hasLocalPin() && vm.verifyLocalPin(pin)) }
                        } else null,
                        validateError = if (screen.mode == "destroy_pin")
                            stringResource(R.string.destroy_pin_same_as_pin)
                        else null,
                        onDone = { pin ->
                            if (screen.mode == "destroy_pin") {
                                vm.setSelfDestructPin(pin)
                            } else {
                                vm.setAppPin(pin)
                            }
                            vm.showToast(context.getString(R.string.pin_saved))
                            vm.nav.pop()
                        },
                        onCancel = { vm.nav.pop() }
                    )

                    is Screen.PinVerify -> CurrentPinVerifyRoute(screen.next)
                }
            }
        }
    }
}
