package com.safekey.authenticator

import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.data.LanguagePrefs
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
                dynamicColor = settings.dynamicColor,
                themeColorIndex = settings.themeColorIndex
            ) {
                Box(Modifier.fillMaxSize()) {
                    when {
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
        vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        vm.onAppBackground()
    }

    // ------------------------------------------------------------ PIN gate

    @Composable
    private fun PinGate() {
        val error by vm.pinError.collectAsState()
        val attempts = vm.remainingAttempts()
        val context = LocalContext.current

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
        val context = LocalContext.current

        LockScreen(
            errorMessage = errorMessage,
            onUnlock = {
                when (canAuthenticate()) {
                    true -> {
                        errorMessage = null
                        launchBiometric(
                            onSuccess = { vm.unlock() },
                            onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                            onError = { errorMessage = context.getString(R.string.lock_failed) }
                        )
                    }
                    false -> errorMessage = context.getString(R.string.biometric_unavailable)
                }
            }
        )

        // Attempt unlock automatically once the gate appears (after the
        // activity is fully resumed — avoids prompt-vs-window races).
        LaunchedEffect(Unit) {
            delay(400)
            if (canAuthenticate() && vm.locked.value) {
                launchBiometric(
                    onSuccess = { vm.unlock() },
                    onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                    onError = { errorMessage = context.getString(R.string.lock_failed) }
                )
            }
        }
    }

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS

    // ------------------------------------------------------- biometric core

    private var biometricPrompt: BiometricPrompt? = null
    private var promptInFlight = false

    /**
     * Single BiometricPrompt instance + re-entry guard. The 1.1.0 crash loop
     * (multiple prompts stacking on ColorOS/Android 15) is prevented by
     * reusing one instance and dropping duplicate requests; all calls are
     * wrapped so a prompt failure can never take the app down.
     */
    private fun launchBiometric(
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: () -> Unit
    ) {
        if (promptInFlight) return
        promptInFlight = true
        try {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptInFlight = false
                    vm.onBiometricSucceeded()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptInFlight = false
                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancelled()
                        else -> onError()
                    }
                }

                override fun onAuthenticationFailed() {
                    // Counts toward the system lockout and the self-destruct
                    // fail counter; UI stays on the gate.
                    vm.onBiometricFailed()
                }
            }
            val prompt = biometricPrompt ?: BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                callback
            ).also { biometricPrompt = it }

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.biometric_prompt_title))
                .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .setNegativeButtonText(getString(R.string.biometric_negative))
                .build()
            prompt.authenticate(info)
        } catch (e: Exception) {
            promptInFlight = false
            onError()
        }
    }

    // ------------------------------------------------------------ nav host

    @Composable
    private fun CurrentPinVerifyRoute() {
        var error by remember { mutableStateOf<String?>(null) }
        PinVerifyScreen(
            title = stringResource(R.string.pin_verify_title),
            subtitle = stringResource(R.string.pin_current_hint),
            error = error,
            remainingAttempts = null,
            onVerify = { pin ->
                if (vm.verifyLocalPin(pin)) {
                    vm.nav.pop()
                    vm.nav.push(Screen.PinSetup("pin"))
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
                        onRequireBiometric = { onSuccess ->
                            if (canAuthenticate()) {
                                launchBiometric(
                                    onSuccess = onSuccess,
                                    onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                    onError = { vm.showToast(context.getString(R.string.lock_failed)) }
                                )
                            } else {
                                // No biometrics on device — degrade gracefully
                                onSuccess()
                            }
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
                        onOpenPinVerify = { vm.nav.push(Screen.PinVerify) },
                        onBiometricChanged = { enable ->
                            if (enable) {
                                if (canAuthenticate()) {
                                    launchBiometric(
                                        onSuccess = { vm.setBiometricLock(true) },
                                        onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                        onError = { vm.showToast(context.getString(R.string.lock_failed)) }
                                    )
                                } else {
                                    vm.showToast(context.getString(R.string.biometric_unavailable))
                                }
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

                    is Screen.PinVerify -> CurrentPinVerifyRoute()
                }
            }
        }
    }
}
