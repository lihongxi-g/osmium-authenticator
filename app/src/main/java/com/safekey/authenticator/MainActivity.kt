package com.safekey.authenticator

import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.safekey.authenticator.backup.AutoBackupScheduler
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.data.LanguagePrefs
import com.safekey.authenticator.integrity.IntegrityLevel
import com.safekey.authenticator.legal.LegalDocsRepository
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.IntegrityCheck
import com.safekey.authenticator.security.RootState
import com.safekey.authenticator.totp.OtpUriParser
import com.safekey.authenticator.ui.components.SwipeBackContainer
import com.safekey.authenticator.ui.components.UpdateAvailableDialog
import com.safekey.authenticator.ui.components.integrityCheckTitle
import com.safekey.authenticator.ui.navigation.Screen
import com.safekey.authenticator.ui.navigation.isRootBlocked
import com.safekey.authenticator.ui.screens.AboutScreen
import com.safekey.authenticator.ui.screens.DeveloperScreen
import com.safekey.authenticator.ui.screens.AccountFormScreen
import com.safekey.authenticator.ui.screens.AccountsScreen
import com.safekey.authenticator.ui.screens.AttributionsScreen
import com.safekey.authenticator.ui.screens.AutoBackupScreen
import com.safekey.authenticator.ui.screens.DetailScreen
import com.safekey.authenticator.ui.screens.ExportScreen
import com.safekey.authenticator.ui.screens.FileImportScreen
import com.safekey.authenticator.ui.screens.GoogleImportScreen
import com.safekey.authenticator.ui.screens.ImportScreen
import com.safekey.authenticator.ui.screens.IntegrityScreen
import com.safekey.authenticator.ui.screens.LanTransferScreen
import com.safekey.authenticator.ui.screens.LockScreen
import com.safekey.authenticator.ui.screens.PinSetupScreen
import com.safekey.authenticator.ui.screens.PinVerifyScreen
import com.safekey.authenticator.ui.screens.ScanScreen
import com.safekey.authenticator.ui.screens.SettingsScreen
import com.safekey.authenticator.ui.screens.TagSettingsScreen
import com.safekey.authenticator.ui.screens.ThirdPartyImportScreen
import com.safekey.authenticator.ui.screens.TagsScreen
import com.safekey.authenticator.ui.screens.ShareQrScreen
import com.safekey.authenticator.ui.screens.SortOrderScreen
import com.safekey.authenticator.ui.screens.ManualScreen
import com.safekey.authenticator.ui.screens.WebDavScreen
import com.safekey.authenticator.ui.theme.SafeKeyTheme
import com.safekey.authenticator.update.UpdateChecker
import com.safekey.authenticator.update.UpdateInfo
import com.safekey.authenticator.update.UpdateResult
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()
    private var tampered = false

    // Update-check state: silent GitHub query on open, one dialog. The dialog
    // also carries the release notes the same request returns.
    private var pendingUpdate by mutableStateOf<UpdateInfo?>(null)
    private var updateCheckInFlight = false

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
            val integrityNotice by vm.integrityNotice.collectAsState()
            val biometricReady by vm.biometricAvailable.collectAsState()
            val pinSet by vm.localPinSet.collectAsState()
            val foregroundTick by vm.foregroundTick.collectAsState()
            var pinReminderVisible by remember { mutableStateOf(false) }

            // No fingerprint/face on this device: ask for an app PIN. The prompt
            // repeats on every app open unless the user ticks "don't remind me".
            LaunchedEffect(foregroundTick, biometricReady, pinSet, settings.pinReminderSilenced) {
                if (!biometricReady && !pinSet && !settings.pinReminderSilenced) {
                    pinReminderVisible = true
                }
            }
            val context = LocalContext.current
            // Keep these UI holders above the lock gate and AnimatedContent.
            // Navigating to a child route, or briefly showing the lock screen,
            // must not reset the user's home/settings scroll positions.
            val accountsListState = rememberLazyListState()
            val accountTagRowState = rememberScrollState()
            val settingsScrollState = rememberScrollState()
            // Per-screen state (e.g. a half-filled manual-add form) survives the
            // gate overlay: the lock gate replaces the whole nav host, which
            // used to wipe everything the user had typed.
            val screenStateHolder = rememberSaveableStateHolder()

            SafeKeyTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                // Screenshot policy follows the user setting (default: blocked).
                LaunchedEffect(settings.allowScreenshots) {
                    if (settings.allowScreenshots) {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        AppLog.d("screenshots enabled by user")
                    } else {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_SECURE,
                            WindowManager.LayoutParams.FLAG_SECURE
                        )
                        AppLog.d("screenshots blocked (default)")
                    }
                }
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
                        else -> MainNavHost(
                            accountsListState = accountsListState,
                            accountTagRowState = accountTagRowState,
                            settingsScrollState = settingsScrollState,
                            screenStateHolder = screenStateHolder
                        )
                    }
                    // Update notification only over the unlocked main UI.
                    val update = pendingUpdate
                    if (update != null && !locked && !pinRequired &&
                        !destroyed && !tampered
                    ) {
                        UpdateAvailableDialog(
                            tag = update.tag,
                            notes = update.notes,
                            url = update.url,
                            onOpenReleasePage = { url ->
                                pendingUpdate = null
                                try {
                                    startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    )
                                } catch (_: Exception) {
                                    vm.showToast(getString(R.string.no_browser))
                                }
                            },
                            onDismiss = { pendingUpdate = null }
                        )
                    }
                    val showPinReminder = pinReminderVisible && !biometricReady && !pinSet &&
                        !settings.pinReminderSilenced && !locked && !pinRequired &&
                        !destroyed && !tampered
                    if (showPinReminder) {
                        PinReminderDialog(
                            onSetup = {
                                pinReminderVisible = false
                                vm.nav.push(Screen.PinSetup("pin"))
                            },
                            onDismiss = { silence ->
                                pinReminderVisible = false
                                if (silence) vm.setPinReminderSilenced(true)
                            }
                        )
                    }
                    // Enter-app integrity reminder (L2/L3), debounced in the
                    // ViewModel; suppressed while the restriction is lifted.
                    val notice = integrityNotice
                    if (notice != null && !locked && !pinRequired && !destroyed && !tampered) {
                        IntegrityNoticeDialog(
                            notice = notice,
                            onViewReport = {
                                vm.dismissIntegrityNotice()
                                vm.nav.push(Screen.Integrity)
                            },
                            onDismiss = { vm.dismissIntegrityNotice() }
                        )
                    }
                }
            }

            LaunchedEffect(toast) {
                toast?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    vm.clearToast()
                }
            }

            // Auto-backup schedule maintenance: runs once the real settings
            // load (NOT in onStart — at that point the DataStore flow may
            // still carry the defaults, which silently skipped the ensure).
            LaunchedEffect(
                settings.autoBackupEnabled,
                settings.autoBackupHour,
                settings.autoBackupMinute,
                settings.autoBackupIntervalDays
            ) {
                if (settings.autoBackupEnabled && !destroyed) {
                    AutoBackupScheduler.ensureScheduled(this@MainActivity, settings)
                    // If a scheduled run was frozen overnight (OEM battery
                    // policies), run one now instead of waiting for the
                    // system to get around to the overdue job.
                    AutoBackupScheduler.maybeCatchUp(this@MainActivity, settings)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setBiometricAvailable(canAuthenticateBiometric())
        vm.onAppForeground()
        maybeCheckForUpdate()
        maybeRefreshLegalDocs()
        // The startup scan feeds the integrity screen, the enter-app reminder
        // and the root-hardening overlay. With the feature hidden in developer
        // mode it must not run behind the user's back either: it stays off
        // until they run the check by hand (developer mode → run root check).
        if (AppSettings.HIDDEN_FEATURE_INTEGRITY !in vm.settings.value.devHiddenFeatures) {
            lifecycleScope.launch { RootState.refresh(applicationContext) }
        }
    }

    override fun onStop() {
        super.onStop()
        // The system biometric prompt dies when the activity stops — on some
        // OEM builds (ColorOS) it dies WITHOUT any callback, which would
        // leave promptInFlight stuck forever and block every future prompt
        // ("can't unlock with fingerprint/password, only PIN" bug).
        // Release the guard explicitly here.
        if (promptInFlight) {
            promptSeq++ // invalidate the 30s watchdog
            promptInFlight = false
            try {
                activePrompt?.cancelAuthentication()
            } catch (_: Exception) {
            }
            activePrompt = null
            restoreSecureFlag()
            AppLog.d("onStop: biometric prompt guard released")
        }
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
                    // Wrong PIN — the self-destruct PIN destroys all data
                    vm.checkSelfDestructPin(pin)
                } else {
                    // A correct PIN is a complete unlock here. Without this the
                    // `locked` flag stayed true (it starts true and every
                    // background sets it), so the biometric LockGate appeared
                    // right after the PIN and asked for a second verification.
                    vm.unlock()
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
        // No biometrics on this device: the only way through is the app PIN, so
        // open straight on the PIN pad instead of the "unlock" button screen,
        // whose biometric call can only fail ("biometric unavailable").
        var pinMode by remember {
            mutableStateOf(!canAuthenticateBiometric() && vm.hasLocalPin())
        }
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
                    if (vm.onPinEntered(pin)) {
                        // correct PIN passes the biometric gate too
                        vm.unlock()
                    } else {
                        vm.checkSelfDestructPin(pin)
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

            // Attempt unlock automatically once the gate appears — only when
            // the activity is fully RESUMED, otherwise the prompt can die
            // without a callback on some OEM builds.
            LaunchedEffect(Unit) {
                delay(400)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    canAuthenticateBiometric() && vm.locked.value
                ) {
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
    private var activePrompt: BiometricPrompt? = null
    private var promptStartedAt = 0L
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
            // Stale-guard self-heal: if the previous prompt has been hanging
            // for more than 8 seconds without any callback (OEM prompt death),
            // force-reset and let this new request through.
            if (System.currentTimeMillis() - promptStartedAt > 8_000) {
                AppLog.d("stale prompt detected — forcing reset")
                promptSeq++
                promptInFlight = false
                try {
                    activePrompt?.cancelAuthentication()
                } catch (_: Exception) {
                }
                activePrompt = null
            } else {
                AppLog.d("prompt skipped: another prompt in flight")
                return
            }
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
                    activePrompt = null
                    restoreSecureFlag()
                    AppLog.d("prompt SUCCESS type=${result.authenticationType}")
                    vm.onBiometricSucceeded()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptSeq++
                    promptInFlight = false
                    activePrompt = null
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
            activePrompt = prompt
            promptStartedAt = System.currentTimeMillis()

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
        // respect the user's screenshot preference when restoring after a
        // biometric prompt (which temporarily clears the flag)
        if (!vm.settings.value.allowScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    // --------------------------------------------------------- update check

    /** Minimum gap between checks (GitHub's unauthenticated rate limit is
     *  60 requests/hour per IP; the user asked for a check on every app
     *  open, so a short invisible floor prevents 429 storms). */
    private var updateCheckCooldownUntil = 0L

    /**
     * Silent check against the GitHub releases API, run in the background
     * every time the app comes to the foreground (when the user has
     * auto-update checks enabled). Failures stay silent; a found update
     * sets [pendingUpdate], which the Compose tree shows as a dialog over
     * the unlocked main UI.
     */
    private fun maybeCheckForUpdate() {
        val settings = vm.settings.value
        if (!settings.autoCheckUpdates || updateCheckInFlight || tampered ||
            vm.destroyed.value
        ) return
        val now = System.currentTimeMillis()
        if (now < updateCheckCooldownUntil) return
        updateCheckCooldownUntil = now + 60_000L
        updateCheckInFlight = true
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { UpdateChecker.check() }
                if (result is UpdateResult.Found) pendingUpdate = result.info
            } finally {
                updateCheckInFlight = false
            }
        }
    }

    // --------------------------------------------------------- legal docs

    /**
     * Reads the latest Terms of Use / Privacy Policy from osmium.im in the
     * background when the app opens. Throttling lives inside
     * [LegalDocsRepository]; failures stay silent here — the About dialogs
     * show a failure notice with retry and a link to the website.
     */
    private fun maybeRefreshLegalDocs() {
        if (tampered || vm.destroyed.value) return
        lifecycleScope.launch {
            LegalDocsRepository.refreshIfDue(applicationContext)
        }
    }

/**
 * Suggestion to set an app PIN on a device without fingerprint/face unlock.
 * The reminder returns on the next app open unless the checkbox is ticked.
 */
@Composable
private fun PinReminderDialog(
    onSetup: () -> Unit,
    onDismiss: (silence: Boolean) -> Unit
) {
    var silence by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDismiss(silence) },
        title = { Text(stringResource(R.string.app_pin_setup)) },
        text = {
            Column {
                Text(stringResource(R.string.pin_reminder_body))
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { silence = !silence }
                ) {
                    Checkbox(checked = silence, onCheckedChange = { silence = it })
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.pin_reminder_never),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSetup) {
                Text(stringResource(R.string.pin_setup_title))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(silence) }) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}

@Composable
private fun IntegrityNoticeDialog(
    notice: IntegrityNotice,
    onViewReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val compromised = notice.level == IntegrityLevel.COMPROMISED
    val reasons = notice.hitIds.map { integrityCheckTitle(it) }.take(3).joinToString(", ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (compromised) R.string.integrity_dialog_title_alert
                    else R.string.integrity_dialog_title_warn
                )
            )
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (compromised) R.string.integrity_dialog_body_alert
                        else R.string.integrity_dialog_body_warn
                    )
                )
                if (reasons.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.integrity_dialog_reasons, reasons),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onViewReport) {
                Text(stringResource(R.string.integrity_dialog_view_report))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.integrity_dialog_dismiss))
            }
        }
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
                // onPinEntered (not verifyLocalPin) so wrong attempts count
                // toward the self-destruct threshold here too.
                if (vm.onPinEntered(pin)) {
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
                    // self-destruct PIN works here too
                    vm.checkSelfDestructPin(pin)
                    error = getString(R.string.pin_wrong)
                }
            },
            onCancel = { vm.nav.pop() }
        )
    }

    @Composable
    private fun MainNavHost(
        accountsListState: LazyListState,
        accountTagRowState: ScrollState,
        settingsScrollState: ScrollState,
        screenStateHolder: androidx.compose.runtime.saveable.SaveableStateHolder
    ) {
        val context = LocalContext.current
        val direction = vm.nav.direction
        val current = vm.nav.current
        val rootRestricted by vm.rootRestricted.collectAsState()

        // Defense in depth: if the restriction becomes active while a blocked
        // screen is open (e.g. restored after a config change), leave it
        // instead of exposing the feature.
        LaunchedEffect(current, rootRestricted) {
            if (rootRestricted && current.isRootBlocked()) {
                vm.nav.popToRoot()
                vm.showToast(context.getString(R.string.root_feature_blocked))
            }
        }

        BackHandler(enabled = vm.nav.canGoBack) { vm.nav.pop() }
        Box(Modifier.fillMaxSize()) {
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
                screenStateHolder.SaveableStateProvider(screen.toString()) {
                when (screen) {
                    is Screen.Accounts -> AccountsScreen(
                        vm = vm,
                        listState = accountsListState,
                        tagRowState = accountTagRowState,
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
                                OtpUriParser.parse(clipText, vm.settings.value.devExtraDigits)
                            } catch (_: Exception) {
                                null
                            }
                            if (parsed != null) {
                                vm.nav.push(Screen.AccountForm(accountId = null, prefill = parsed))
                            } else {
                                if (clipText.contains("Steam", ignoreCase = true)) {
                                    vm.showToast(context.getString(R.string.steam_manual_hint))
                                } else {
                                    vm.showToast(context.getString(R.string.error_uri_invalid))
                                }
                            }
                        },
                        onOpenDetail = { account -> vm.nav.push(Screen.Detail(account.id)) },
                        onOpenSettings = { vm.nav.push(Screen.Settings) }
                    )

                    is Screen.AccountForm -> AccountFormScreen(
                        vm = vm,
                        accountId = screen.accountId,
                        prefillUri = screen.prefill,
                        // Leaving the form on purpose drops its draft, so
                        // returning to edit the same account shows the stored
                        // values rather than the previous typing.
                        onDone = {
                            screenStateHolder.removeState(screen.toString())
                            vm.nav.pop()
                        },
                        onBack = {
                            screenStateHolder.removeState(screen.toString())
                            vm.nav.pop()
                        }
                    )

                    is Screen.Detail -> DetailScreen(
                        vm = vm,
                        accountId = screen.accountId,
                        onEdit = { account -> vm.nav.push(Screen.AccountForm(account.id)) },
                        onDeleted = { vm.nav.popToRoot() },
                        onBack = { vm.nav.pop() },
                        onShare = { account -> vm.nav.push(Screen.ShareQr(account.id)) },
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
                        scrollState = settingsScrollState,
                        onBack = { vm.nav.pop() },
                        onExport = { vm.nav.push(Screen.Export) },
                        onImport = { vm.nav.push(Screen.Import) },
                        onWebDav = { vm.nav.push(Screen.WebDav) },
                        onAutoBackup = { vm.nav.push(Screen.AutoBackup) },
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
                        onRequireCredential = { onSuccess ->
                            launchCredential(
                                onSuccess = onSuccess,
                                onCancelled = { vm.showToast(context.getString(R.string.lock_cancelled)) },
                                onError = { msg -> vm.showToast(msg) }
                            )
                        },
                        onIntegrity = { vm.nav.push(Screen.Integrity) },
                        onLanguageChanged = { lang ->
                            LanguagePrefs.set(this@MainActivity, lang)
                            recreate()
                        }
                    )

                    is Screen.TagSettings -> TagSettingsScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
                        onManageTags = { vm.nav.push(Screen.Tags) }
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

                    is Screen.Integrity -> IntegrityScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.WebDav -> WebDavScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.AutoBackup -> AutoBackupScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.LanTransfer -> LanTransferScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
                        // Blocking a peer device is a security decision: verify
                        // identity first (same pattern as the settings toggles).
                        onRequireBiometric = if (canAuthenticateBiometric()) {
                            { onSuccess ->
                                launchBiometric(
                                    onSuccess = onSuccess,
                                    onCancelled = {
                                        vm.showToast(context.getString(R.string.lock_cancelled))
                                    },
                                    onError = { msg -> vm.showToast(msg) }
                                )
                            }
                        } else null,
                        onRequireCredential = { onSuccess ->
                            launchCredential(
                                onSuccess = onSuccess,
                                onCancelled = {
                                    vm.showToast(context.getString(R.string.lock_cancelled))
                                },
                                onError = { msg -> vm.showToast(msg) }
                            )
                        }
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

                    is Screen.ShareQr -> ShareQrScreen(
                        vm = vm,
                        accountId = screen.accountId,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.About -> AboutScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
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

                    is Screen.Developer -> DeveloperScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
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

                    is Screen.Manual -> ManualScreen(
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.SortOrder -> SortOrderScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.GoogleImport -> GoogleImportScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
                        onImported = { vm.nav.pop() }
                    )

                    is Screen.FileImport -> FileImportScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() },
                        onImported = { vm.nav.pop() }
                    )

                    is Screen.ThirdPartyImport -> ThirdPartyImportScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.Attributions -> AttributionsScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )

                    is Screen.Tags -> TagsScreen(
                        vm = vm,
                        onBack = { vm.nav.pop() }
                    )
                }
                }
            }
        }
    }
}
}
