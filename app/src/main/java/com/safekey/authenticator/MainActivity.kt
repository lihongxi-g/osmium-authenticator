package com.safekey.authenticator

import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.safekey.authenticator.totp.OtpUriParser
import com.safekey.authenticator.ui.components.SwipeBackContainer
import com.safekey.authenticator.ui.navigation.Screen
import com.safekey.authenticator.ui.screens.AccountFormScreen
import com.safekey.authenticator.ui.screens.AccountsScreen
import com.safekey.authenticator.ui.screens.DetailScreen
import com.safekey.authenticator.ui.screens.ExportScreen
import com.safekey.authenticator.ui.screens.ImportScreen
import com.safekey.authenticator.ui.screens.LockScreen
import com.safekey.authenticator.ui.screens.ScanScreen
import com.safekey.authenticator.ui.screens.SettingsScreen
import com.safekey.authenticator.ui.theme.SafeKeyTheme
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

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
            val toast by vm.toast.collectAsState()
            val context = LocalContext.current

            SafeKeyTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Box(Modifier.fillMaxSize()) {
                    if (locked) {
                        LockGate()
                    } else {
                        MainNavHost()
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

    // ------------------------------------------------------------ lock gate

    @Composable
    private fun LockGate() {
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val context = LocalContext.current

        LockScreen(
            errorMessage = errorMessage,
            onUnlock = {
                when (BiometricManager.from(this).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        errorMessage = null
                        showBiometricPrompt(
                            onSuccess = { vm.unlock() },
                            onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                            onError = { errorMessage = context.getString(R.string.lock_failed) }
                        )
                    }
                    else -> {
                        errorMessage = context.getString(R.string.biometric_unavailable)
                    }
                }
            }
        )

        // Attempt unlock automatically once when the gate first appears
        LaunchedEffect(Unit) {
            if (BiometricManager.from(this@MainActivity).canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ) == BiometricManager.BIOMETRIC_SUCCESS
            ) {
                showBiometricPrompt(
                    onSuccess = { vm.unlock() },
                    onCancelled = { errorMessage = context.getString(R.string.lock_cancelled) },
                    onError = { errorMessage = context.getString(R.string.lock_failed) }
                )
            }
        }
    }

    private fun showBiometricPrompt(
        onSuccess: () -> Unit,
        onCancelled: () -> Unit,
        onError: () -> Unit
    ) {
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancelled()
                        else -> onError()
                    }
                }

                override fun onAuthenticationFailed() {
                    // Counts toward the system's lockout; UI stays on the gate.
                }
            }
        )
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
    }

    // ------------------------------------------------------------ nav host

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
                            if (BiometricManager.from(this@MainActivity).canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                ) == BiometricManager.BIOMETRIC_SUCCESS
                            ) {
                                showBiometricPrompt(
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
                        onBiometricChanged = { enable ->
                            if (enable) {
                                when (BiometricManager.from(this@MainActivity).canAuthenticate(
                                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                                )) {
                                    BiometricManager.BIOMETRIC_SUCCESS -> {
                                        showBiometricPrompt(
                                            onSuccess = { vm.setBiometricLock(true) },
                                            onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                            onError = { vm.showToast(context.getString(R.string.lock_failed)) }
                                        )
                                    }
                                    else -> {
                                        vm.showToast(context.getString(R.string.biometric_unavailable))
                                    }
                                }
                            }
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
                }
            }
        }
    }
}
