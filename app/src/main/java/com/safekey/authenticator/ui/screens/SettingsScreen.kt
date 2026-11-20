package com.safekey.authenticator.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.ClipboardHelper
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.integrityLevelLabel
import com.safekey.authenticator.ui.dev.DevStrings
import com.safekey.authenticator.ui.navigation.Screen

/**
 * Developer-mode ids that live on the appearance page. The main list shows the
 * single entry row as long as at least one of them is still visible.
 */
private val APPEARANCE_FEATURES =
    listOf("theme", "fontFamily", "fontSize", "haptic", "predictiveBack", "sort", "tags", "language")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    scrollState: ScrollState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onWebDav: () -> Unit,
    onAutoBackup: () -> Unit,
    onIntegrity: () -> Unit,
    onOpenPinSetup: () -> Unit,
    onOpenPinVerify: (String) -> Unit,
    onRequireBiometric: ((onSuccess: () -> Unit) -> Unit)? = null,
    onRequireCredential: ((onSuccess: () -> Unit) -> Unit)? = null,
    /**
     * Same two prompts, but only passed when the method really verifies on this
     * device (biometrics enrolled / screen lock set). The Verification settings
     * entry must not fall through when there is nothing to verify with, so it
     * uses these instead of the pair above.
     */
    onRequireBiometricStrict: ((onSuccess: () -> Unit) -> Unit)? = null,
    onRequireCredentialStrict: ((onSuccess: () -> Unit) -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val context = LocalContext.current
    val hasPin = vm.hasLocalPin()
    val hasDestroyPin = vm.pinManager.hasDestroyPin()
    val rootRestricted by vm.rootRestricted.collectAsState()
    val rootReport by vm.rootReport.collectAsState()
    val dev = DevStrings.forContext(context)
    // Developer-mode "hide entries" set (ids match DeveloperScreen.HIDEABLE_ITEMS)
    val hiddenFeatures = settings.devHiddenFeatures

    var showDestroyModeDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var offsetInput by remember { mutableStateOf("") }
    // sensitive toggle awaiting verification: Pair("gate"/"screenshot", target)
    var pendingToggle by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // navigation awaiting verification (e.g. entering the WebDAV screen)
    var pendingNav by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    // true while the prompt was opened for the Verification settings page: only
    // the strict callbacks count as a real check there.
    var verifyStrict by remember { mutableStateOf(false) }
    var showNoMethodDialog by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    // "feature" / "setting" while the root-blocked notice dialog is showing
    var rootDialogTarget by remember { mutableStateOf<String?>(null) }

    fun executeToggle(p: Pair<String, Boolean>) {
        when (p.first) {
            "gate" -> vm.setGateOnOpen(p.second)
            "screenshot" -> vm.setAllowScreenshots(p.second)
            "hideCodes" -> vm.setHideCodes(p.second)
        }
    }

    /**
     * True when [id] (an AppSettings.VERIFY_* id) still asks for verification.
     * The always-on ids and anything the user did not turn off keep the check;
     * the root restriction clears the opt-out set in the view model.
     */
    fun verifyRequired(id: String): Boolean =
        id in AppSettings.VERIFY_ALWAYS_ON || id !in settings.verifyOptOut

    /** Queue [action] behind an identity check and open the method chooser. */
    fun askVerify(strict: Boolean = false, action: () -> Unit) {
        pendingNav = action
        verifyStrict = strict
        showVerifyDialog = true
    }

    /** Same for a settings toggle waiting on verification. */
    fun askVerifyToggle(kind: String, target: Boolean) {
        pendingToggle = kind to target
        verifyStrict = false
        showVerifyDialog = true
    }

    /**
     * Run whatever verification-protected action is pending (toggle or nav).
     *
     * The pending target is copied to a local BEFORE the state is cleared: the
     * old code cleared it inside executeToggle and lost the target whenever the
     * callback fired late (the "switch bounces back" bug).
     */
    fun executePending() {
        val nav = pendingNav
        val toggle = pendingToggle
        pendingNav = null
        pendingToggle = null
        verifyStrict = false
        if (nav != null) nav() else if (toggle != null) executeToggle(toggle)
    }

    /**
     * True when [kind] ("feature" / "setting") is blocked by the active root
     * restriction — shows the notice dialog instead of running the action.
     */
    fun rootBlocked(kind: String): Boolean {
        if (!rootRestricted) return false
        rootDialogTarget = kind
        return true
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.settings_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))

            // Appearance is one page now: its rows had grown to six here, and
            // each of them is a page of its own one level down.
            if (APPEARANCE_FEATURES.any { it !in hiddenFeatures }) SettingRow(
                icon = AppIcons.Palette,
                title = stringResource(R.string.settings_appearance),
                description = stringResource(R.string.appearance_desc),
                trailing = {
                    Text(
                        text = when (settings.themeMode) {
                            AppSettings.THEME_LIGHT -> stringResource(R.string.theme_light)
                            AppSettings.THEME_DARK -> stringResource(R.string.theme_dark)
                            else -> stringResource(R.string.theme_system)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.Appearance) }
            )

            SectionHeader(stringResource(R.string.settings_security))

            if (rootRestricted) {
                Text(
                    text = stringResource(R.string.root_section_locked),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if ("gate" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Fingerprint,
                title = stringResource(R.string.gate_on_open),
                description = stringResource(R.string.gate_on_open_desc),
                trailing = {
                    Switch(
                        checked = settings.gateOnOpen,
                        onCheckedChange = { target ->
                            if (!rootBlocked("setting")) {
                                if (verifyRequired(AppSettings.VERIFY_GATE)) {
                                    askVerifyToggle("gate", target)
                                } else {
                                    vm.setGateOnOpen(target)
                                }
                            }
                        }
                    )
                }
            )

            if ("screenshots" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Visibility,
                title = stringResource(R.string.allow_screenshots),
                description = stringResource(R.string.allow_screenshots_desc),
                trailing = {
                    Switch(
                        checked = settings.allowScreenshots,
                        onCheckedChange = { target ->
                            if (!rootBlocked("setting")) {
                                if (verifyRequired(AppSettings.VERIFY_SCREENSHOT)) {
                                    askVerifyToggle("screenshot", target)
                                } else {
                                    vm.setAllowScreenshots(target)
                                }
                            }
                        }
                    )
                }
            )

            if ("hideCodes" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Visibility,
                title = stringResource(R.string.hide_codes),
                description = stringResource(R.string.hide_codes_desc),
                trailing = {
                    Switch(
                        checked = settings.hideCodes,
                        onCheckedChange = { target ->
                            if (!rootBlocked("setting")) {
                                if (verifyRequired(AppSettings.VERIFY_HIDE_CODES)) {
                                    askVerifyToggle("hideCodes", target)
                                } else {
                                    vm.setHideCodes(target)
                                }
                            }
                        }
                    )
                }
            )

            if ("timeOffset" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Timer,
                title = stringResource(R.string.time_offset),
                description = stringResource(
                    R.string.time_offset_desc,
                    settings.timeOffsetSeconds
                ),
                onClick = {
                    offsetInput = settings.timeOffsetSeconds.toString()
                    showOffsetDialog = true
                }
            )

            if ("integrity" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Security,
                title = stringResource(R.string.integrity_entry_title),
                description = stringResource(R.string.integrity_entry_desc),
                trailing = {
                    Text(
                        text = rootReport?.let { integrityLevelLabel(it.level) }
                            ?: stringResource(R.string.loading),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = onIntegrity
            )

            // Which sensitive actions still verify. Opening it always verifies
            // (it can only weaken checks) and it is closed on a rooted device.
            if ("verifySettings" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Fingerprint,
                title = stringResource(R.string.verify_settings_title),
                description = stringResource(R.string.verify_settings_desc),
                onClick = {
                    if (!rootBlocked("feature")) {
                        if (onRequireBiometricStrict == null &&
                            onRequireCredentialStrict == null &&
                            !hasPin
                        ) {
                            // Nothing on this device can verify — do not open.
                            showNoMethodDialog = true
                        } else {
                            askVerify(strict = true) { vm.nav.push(Screen.VerifySettings) }
                        }
                    }
                }
            )

            // -------------------------------------------------------- PIN

            if ("pin" !in hiddenFeatures) SectionHeader(stringResource(R.string.settings_pin))

            if ("pin" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Keyboard,
                title = if (hasPin) stringResource(R.string.app_pin_manage) else stringResource(R.string.app_pin_setup),
                description = stringResource(R.string.app_pin_desc),
                onClick = {
                    if (hasPin) onOpenPinVerify("change_pin") else onOpenPinSetup()
                }
            )

            // ------------------------------------------------- self-destruct

            if ("destroy" !in hiddenFeatures) SectionHeader(stringResource(R.string.settings_destroy))

            if ("destroy" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Warning,
                title = stringResource(R.string.destroy_mode),
                description = stringResource(R.string.destroy_mode_desc),
                trailing = {
                    Text(
                        text = when (settings.destroyMode) {
                            AppSettings.DESTROY_PIN -> stringResource(R.string.destroy_mode_pin)
                            AppSettings.DESTROY_FAIL_COUNT -> stringResource(R.string.destroy_mode_failures)
                            else -> stringResource(R.string.clipboard_off)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { if (!rootBlocked("feature")) showDestroyModeDialog = true }
            )

            if ("destroy" !in hiddenFeatures && settings.destroyMode == AppSettings.DESTROY_PIN) {
                SettingRow(
                    icon = AppIcons.VpnKey,
                    title = if (hasDestroyPin) stringResource(R.string.destroy_pin_manage)
                    else stringResource(R.string.destroy_pin_setup),
                    description = stringResource(R.string.destroy_pin_desc),
                    onClick = {
                        if (!rootBlocked("feature")) {
                            if (hasPin) {
                                onOpenPinVerify(if (hasDestroyPin) "change_destroy_pin" else "set_destroy_pin")
                            } else {
                                vm.nav.push(Screen.PinSetup("destroy_pin"))
                            }
                        }
                    }
                )
                if (hasDestroyPin) {
                    Text(
                        text = stringResource(R.string.destroy_pin_clear_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { if (!rootBlocked("feature")) onOpenPinVerify("clear_destroy_pin") }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            if ("destroy" !in hiddenFeatures && settings.destroyMode == AppSettings.DESTROY_FAIL_COUNT) {
                SettingRow(
                    icon = AppIcons.Refresh,
                    title = stringResource(R.string.fail_threshold),
                    description = stringResource(R.string.fail_threshold_desc),
                    trailing = {
                        Text(
                            text = settings.failThreshold.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { if (!rootBlocked("feature")) showThresholdDialog = true }
                )
            }

            if (hasPin && "pin" !in hiddenFeatures) {
                Text(
                    text = stringResource(R.string.pin_clear_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { onOpenPinVerify("clear_pin") }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SectionHeader(stringResource(R.string.settings_data))

            if ("thirdparty" !in hiddenFeatures) SettingRow(
                icon = AppIcons.ImportExport,
                title = stringResource(R.string.thirdparty_import_title),
                description = stringResource(R.string.thirdparty_import_desc),
                onClick = { if (!rootBlocked("feature")) vm.nav.push(Screen.ThirdPartyImport) }
            )

            if ("export" !in hiddenFeatures) SettingRow(
                icon = AppIcons.FileUpload,
                title = stringResource(R.string.export_vault),
                description = stringResource(R.string.export_vault_desc),
                onClick = {
                    // Exporting writes every secret to a file (plaintext when the
                    // developer-mode export is armed): same identity check as the
                    // other sensitive entries on this screen.
                    if (!rootBlocked("feature")) {
                        if (verifyRequired(AppSettings.VERIFY_EXPORT)) {
                            askVerify { onExport() }
                        } else {
                            onExport()
                        }
                    }
                }
            )

            if ("export" !in hiddenFeatures) SettingRow(
                icon = AppIcons.ImportExport,
                title = stringResource(R.string.export_other_title),
                description = stringResource(R.string.export_other_desc),
                onClick = {
                    // Same identity check as the encrypted export: this writes
                    // every secret to a PLAINTEXT file.
                    if (!rootBlocked("feature")) {
                        if (verifyRequired(AppSettings.VERIFY_EXPORT_OTHER)) {
                            askVerify { vm.nav.push(Screen.ExportToOthers) }
                        } else {
                            vm.nav.push(Screen.ExportToOthers)
                        }
                    }
                }
            )

            if ("import" !in hiddenFeatures) SettingRow(
                icon = AppIcons.FileDownload,
                title = stringResource(R.string.import_vault),
                description = stringResource(R.string.import_vault_desc),
                onClick = {
                    if (!rootBlocked("feature")) {
                        if (verifyRequired(AppSettings.VERIFY_IMPORT)) {
                            askVerify { onImport() }
                        } else {
                            onImport()
                        }
                    }
                }
            )

            if ("lan" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Wifi,
                title = stringResource(R.string.lan_transfer_title),
                description = stringResource(R.string.lan_transfer_desc),
                onClick = {
                    if (!rootBlocked("feature")) {
                        if (verifyRequired(AppSettings.VERIFY_LAN)) {
                            askVerify { vm.nav.push(Screen.LanTransfer) }
                        } else {
                            vm.nav.push(Screen.LanTransfer)
                        }
                    }
                }
            )

            if ("webdav" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Dns,
                title = stringResource(R.string.webdav_title),
                description = stringResource(R.string.webdav_desc),
                onClick = {
                    if (!rootBlocked("feature")) {
                        // Entering the WebDAV screen requires identity verification
                        if (verifyRequired(AppSettings.VERIFY_WEBDAV)) {
                            askVerify { onWebDav() }
                        } else {
                            onWebDav()
                        }
                    }
                }
            )

            if ("autobackup" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Timer,
                title = stringResource(R.string.auto_backup_title),
                description = stringResource(R.string.auto_backup_desc),
                onClick = {
                    if (!rootBlocked("feature")) {
                        if (verifyRequired(AppSettings.VERIFY_AUTO_BACKUP)) {
                            askVerify { onAutoBackup() }
                        } else {
                            onAutoBackup()
                        }
                    }
                }
            )

            SettingRow(
                icon = AppIcons.SwapVert,
                title = stringResource(R.string.accounts_count, accounts.size)
            )

            // Its own section, deliberately not nested under About: this is the
            // single place to see and grant everything the app asks the system
            // for, and it does not belong between "version" and "license".
            SectionHeader(stringResource(R.string.permissions_title))
            SettingRow(
                icon = AppIcons.Security,
                title = stringResource(R.string.permissions_manage_title),
                description = stringResource(R.string.permissions_desc),
                onClick = { vm.nav.push(Screen.Permissions) }
            )

            SectionHeader(stringResource(R.string.settings_about))

            if ("log" !in hiddenFeatures) SettingRow(
                icon = AppIcons.BugReport,
                title = stringResource(R.string.export_log),
                description = stringResource(R.string.export_log_desc),
                onClick = {
                    val text = AppLog.exportText()
                    ClipboardHelper.copy(context, text)
                    AppLog.d("log exported to clipboard (${text.length} chars)")
                    vm.showToast(context.getString(R.string.export_log_done))
                }
            )

            if ("manual" !in hiddenFeatures) SettingRow(
                icon = AppIcons.MenuBook,
                title = stringResource(R.string.manual_title),
                description = stringResource(R.string.manual_entry_desc),
                onClick = { vm.nav.push(Screen.Manual) }
            )

            SettingRow(
                icon = AppIcons.Info,
                title = stringResource(R.string.settings_about),
                description = stringResource(R.string.about_entry_desc),
                trailing = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { vm.nav.push(Screen.About) }
            )

            // Two channels (stable / preview) live on their own page: one row
            // here, two switches there.
            if ("updates" !in hiddenFeatures) SettingRow(
                icon = AppIcons.Refresh,
                title = stringResource(R.string.update_settings_title),
                description = stringResource(R.string.update_settings_desc),
                onClick = { vm.nav.push(Screen.UpdateSettings) }
            )

            // ------------------------------------------ developer mode (2.4.2)

            if (settings.devModeEnabled) {
                SectionHeader(dev.sectionTitle)

                SettingRow(
                    icon = AppIcons.Security,
                    title = dev.sectionTitle,
                    description = dev.entryDesc,
                    onClick = {
                        // Identity verification is required on EVERY entry.
                        askVerify { vm.nav.push(Screen.Developer) }
                    }
                )
            }
        }
    }

    // ---- PIN entry overlay for sensitive toggles ----
    if (pinMode) {
        val attempts = vm.remainingAttempts()
        PinVerifyScreen(
            title = stringResource(R.string.pin_verify_title),
            subtitle = stringResource(R.string.pin_verify_subtitle),
            error = pinError,
            remainingAttempts = attempts,
            onVerify = { pin ->
                if (vm.onPinEntered(pin)) {
                    pinMode = false
                    pinError = null
                    executePending()
                } else {
                    pinError = context.getString(R.string.pin_wrong)
                    vm.checkSelfDestructPin(pin)
                }
            },
            onCancel = {
                pinMode = false
                pinError = null
                pendingToggle = null
                pendingNav = null
                verifyStrict = false
            }
        )
    }

    // ---- verification method chooser for sensitive toggles ----
    if (showVerifyDialog && (pendingToggle != null || pendingNav != null) && !pinMode) {
        // The Verification settings entry (and anything else that must not fall
        // through) only accepts methods that really verify on this device.
        val bio = if (verifyStrict) onRequireBiometricStrict else onRequireBiometric
        val cred = if (verifyStrict) onRequireCredentialStrict else onRequireCredential
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.verify_title)) },
            text = {
                Column {
                    if (bio != null) {
                        VerifyOptionRow(AppIcons.Fingerprint, stringResource(R.string.verify_biometric)) {
                            showVerifyDialog = false
                            bio { executePending() }
                        }
                    }
                    if (cred != null) {
                        VerifyOptionRow(AppIcons.Security, stringResource(R.string.verify_credential)) {
                            showVerifyDialog = false
                            cred { executePending() }
                        }
                    }
                    if (hasPin) {
                        VerifyOptionRow(AppIcons.Keyboard, stringResource(R.string.verify_pin)) {
                            showVerifyDialog = false
                            pinMode = true
                            pinError = null
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showVerifyDialog = false
                    pendingToggle = null
                    pendingNav = null
                    verifyStrict = false
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ---- "nothing can verify" notice ----
    if (showNoMethodDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.verify_no_method_title)) },
            text = { Text(stringResource(R.string.verify_no_method_message)) },
            confirmButton = {
                TextButton(onClick = { showNoMethodDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
    }

    // ------------------------------------------------------------ dialogs


    if (showDestroyModeDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.destroy_mode)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.destroy_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    ThemeOption(stringResource(R.string.clipboard_off), AppSettings.DESTROY_OFF, settings.destroyMode) {
                        vm.setDestroyMode(it)
                        showDestroyModeDialog = false
                    }
                    ThemeOption(stringResource(R.string.destroy_mode_pin), AppSettings.DESTROY_PIN, settings.destroyMode) {
                        vm.setDestroyMode(it)
                        showDestroyModeDialog = false
                    }
                    ThemeOption(stringResource(R.string.destroy_mode_failures), AppSettings.DESTROY_FAIL_COUNT, settings.destroyMode) {
                        vm.setDestroyMode(it)
                        showDestroyModeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDestroyModeDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showOffsetDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.time_offset)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = stringResource(R.string.time_offset_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = offsetInput,
                        onValueChange = { offsetInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = offsetInput.toIntOrNull()
                    if (v != null && v in -600..600) {
                        vm.setTimeOffsetSeconds(v)
                        showOffsetDialog = false
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showOffsetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.fail_threshold)) },
            text = {
                Column {
                    listOf(3, 5, 10).forEach { t ->
                        ThemeOption(t.toString(), t, settings.failThreshold) {
                            vm.setFailThreshold(it)
                            showThresholdDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThresholdDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    val dialogKind = rootDialogTarget
    if (dialogKind != null) {
        AlertDialog(
            onDismissRequest = {},
            text = {
                Text(
                    stringResource(
                        if (dialogKind == "setting") R.string.root_setting_locked
                        else R.string.root_feature_blocked
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { rootDialogTarget = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
private fun VerifyOptionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun <T> ThemeOption(label: String, value: T, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
