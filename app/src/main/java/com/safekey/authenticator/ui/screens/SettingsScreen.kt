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
import com.safekey.authenticator.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onWebDav: () -> Unit,
    onAutoBackup: () -> Unit,
    onOpenPinSetup: () -> Unit,
    onOpenPinVerify: (String) -> Unit,
    onRequireBiometric: ((onSuccess: () -> Unit) -> Unit)? = null,
    onRequireCredential: ((onSuccess: () -> Unit) -> Unit)? = null,
    onLanguageChanged: ((String?) -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val context = LocalContext.current
    val hasPin = vm.hasLocalPin()
    val hasDestroyPin = vm.pinManager.hasDestroyPin()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDestroyModeDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var offsetInput by remember { mutableStateOf("") }
    // sensitive toggle awaiting verification: Pair("gate"/"screenshot", target)
    var pendingToggle by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // navigation awaiting verification (e.g. entering the WebDAV screen)
    var pendingNav by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showVerifyDialog by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun executeToggle() {
        // pendingToggle survives until verification completes — clearing it
        // before the verify callback fires loses the target (the "switch
        // bounces back" bug).
        val p = pendingToggle ?: return
        pendingToggle = null
        when (p.first) {
            "gate" -> vm.setGateOnOpen(p.second)
            "screenshot" -> vm.setAllowScreenshots(p.second)
            "hideCodes" -> vm.setHideCodes(p.second)
        }
    }

    /** Run whatever verification-protected action is pending (toggle or nav). */
    fun executePending() {
        val nav = pendingNav
        if (nav != null) {
            pendingNav = null
            nav()
            return
        }
        executeToggle()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.settings_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))

            SettingRow(
                icon = if (settings.themeMode == AppSettings.THEME_DARK) AppIcons.DarkMode else AppIcons.LightMode,
                title = stringResource(R.string.theme_mode),
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
                onClick = { showThemeDialog = true }
            )

            SettingRow(
                icon = AppIcons.Palette,
                title = stringResource(R.string.dynamic_color),
                description = stringResource(R.string.dynamic_color_desc),
                trailing = {
                    Switch(
                        checked = settings.dynamicColor,
                        onCheckedChange = { vm.setDynamicColor(it) }
                    )
                }
            )

            SettingRow(
                icon = AppIcons.SwapVert,
                title = stringResource(R.string.sort_mode),
                description = stringResource(R.string.sort_mode_desc),
                onClick = { vm.nav.push(Screen.SortOrder) }
            )

            SettingRow(
                icon = AppIcons.Palette,
                title = stringResource(R.string.tags_title),
                description = stringResource(R.string.tags_empty),
                onClick = { vm.nav.push(Screen.Tags) }
            )

            SettingRow(
                icon = AppIcons.Settings,
                title = stringResource(R.string.link_title),
                description = stringResource(R.string.link_trust_warning),
                onClick = { vm.nav.push(Screen.Link) }
            )

            SettingRow(
                icon = AppIcons.Language,
                title = stringResource(R.string.language),
                trailing = {
                    Text(
                        text = when (com.safekey.authenticator.data.LanguagePrefs.get(context)) {
                            "zh" -> stringResource(R.string.lang_zh)
                            "en" -> stringResource(R.string.lang_en)
                            "es" -> stringResource(R.string.lang_es)
                            "ja" -> stringResource(R.string.lang_ja)
                            "ko" -> stringResource(R.string.lang_ko)
                            "de" -> stringResource(R.string.lang_de)
                            "ru" -> stringResource(R.string.lang_ru)
                            "fr" -> stringResource(R.string.lang_fr)
                            "hi" -> stringResource(R.string.lang_hi)
                            else -> stringResource(R.string.lang_system)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showLanguageDialog = true }
            )

            SectionHeader(stringResource(R.string.settings_security))

            SettingRow(
                icon = AppIcons.Fingerprint,
                title = stringResource(R.string.gate_on_open),
                description = stringResource(R.string.gate_on_open_desc),
                trailing = {
                    Switch(
                        checked = settings.gateOnOpen,
                        onCheckedChange = { target ->
                            pendingToggle = "gate" to target
                            showVerifyDialog = true
                        }
                    )
                }
            )

            SettingRow(
                icon = AppIcons.Visibility,
                title = stringResource(R.string.allow_screenshots),
                description = stringResource(R.string.allow_screenshots_desc),
                trailing = {
                    Switch(
                        checked = settings.allowScreenshots,
                        onCheckedChange = { target ->
                            pendingToggle = "screenshot" to target
                            showVerifyDialog = true
                        }
                    )
                }
            )

            SettingRow(
                icon = AppIcons.Visibility,
                title = stringResource(R.string.hide_codes),
                description = stringResource(R.string.hide_codes_desc),
                trailing = {
                    Switch(
                        checked = settings.hideCodes,
                        onCheckedChange = { target ->
                            pendingToggle = "hideCodes" to target
                            showVerifyDialog = true
                        }
                    )
                }
            )

            SettingRow(
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

            // -------------------------------------------------------- PIN

            SectionHeader(stringResource(R.string.settings_pin))

            SettingRow(
                icon = AppIcons.Keyboard,
                title = if (hasPin) stringResource(R.string.app_pin_manage) else stringResource(R.string.app_pin_setup),
                description = stringResource(R.string.app_pin_desc),
                onClick = {
                    if (hasPin) onOpenPinVerify("change_pin") else onOpenPinSetup()
                }
            )

            // ------------------------------------------------- self-destruct

            SectionHeader(stringResource(R.string.settings_destroy))

            SettingRow(
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
                onClick = { showDestroyModeDialog = true }
            )

            if (settings.destroyMode == AppSettings.DESTROY_PIN) {
                SettingRow(
                    icon = AppIcons.VpnKey,
                    title = if (hasDestroyPin) stringResource(R.string.destroy_pin_manage)
                    else stringResource(R.string.destroy_pin_setup),
                    description = stringResource(R.string.destroy_pin_desc),
                    onClick = {
                        if (hasPin) {
                            onOpenPinVerify(if (hasDestroyPin) "change_destroy_pin" else "set_destroy_pin")
                        } else {
                            vm.nav.push(Screen.PinSetup("destroy_pin"))
                        }
                    }
                )
                if (hasDestroyPin) {
                    Text(
                        text = stringResource(R.string.destroy_pin_clear_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { onOpenPinVerify("clear_destroy_pin") }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            if (settings.destroyMode == AppSettings.DESTROY_FAIL_COUNT) {
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
                    onClick = { showThresholdDialog = true }
                )
            }

            if (hasPin) {
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

            SettingRow(
                icon = AppIcons.ImportExport,
                title = stringResource(R.string.migration_title),
                description = stringResource(R.string.migration_desc),
                onClick = { vm.nav.push(Screen.GoogleImport) }
            )

            SettingRow(
                icon = AppIcons.FileUpload,
                title = stringResource(R.string.export_vault),
                description = stringResource(R.string.export_vault_desc),
                onClick = onExport
            )

            SettingRow(
                icon = AppIcons.FileDownload,
                title = stringResource(R.string.import_vault),
                description = stringResource(R.string.import_vault_desc),
                onClick = onImport
            )

            SettingRow(
                icon = AppIcons.Dns,
                title = stringResource(R.string.webdav_title),
                description = stringResource(R.string.webdav_desc),
                onClick = {
                    // Entering the WebDAV screen requires identity verification
                    pendingNav = { onWebDav() }
                    showVerifyDialog = true
                }
            )

            SettingRow(
                icon = AppIcons.Timer,
                title = stringResource(R.string.auto_backup_title),
                description = stringResource(R.string.auto_backup_desc),
                onClick = {
                    pendingNav = { onAutoBackup() }
                    showVerifyDialog = true
                }
            )

            SettingRow(
                icon = AppIcons.SwapVert,
                title = stringResource(R.string.accounts_count, accounts.size)
            )

            SectionHeader(stringResource(R.string.settings_about))

            SettingRow(
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

            SettingRow(
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

            SettingRow(
                icon = AppIcons.Refresh,
                title = stringResource(R.string.update_check_label),
                description = stringResource(R.string.update_check_desc),
                trailing = {
                    Switch(
                        checked = settings.autoCheckUpdates,
                        onCheckedChange = { vm.setAutoCheckUpdates(it) }
                    )
                }
            )
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
            }
        )
    }

    // ---- verification method chooser for sensitive toggles ----
    if (showVerifyDialog && (pendingToggle != null || pendingNav != null) && !pinMode) {
        AlertDialog(
            onDismissRequest = {
                showVerifyDialog = false
                pendingToggle = null
                pendingNav = null
            },
            title = { Text(stringResource(R.string.verify_title)) },
            text = {
                Column {
                    if (onRequireBiometric != null) {
                        VerifyOptionRow(AppIcons.Fingerprint, stringResource(R.string.verify_biometric)) {
                            showVerifyDialog = false
                            onRequireBiometric { executePending() }
                        }
                    }
                    if (onRequireCredential != null) {
                        VerifyOptionRow(AppIcons.Security, stringResource(R.string.verify_credential)) {
                            showVerifyDialog = false
                            onRequireCredential { executePending() }
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
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    }

    // ------------------------------------------------------------ dialogs

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(stringResource(R.string.theme_mode)) },
            text = {
                Column {
                    ThemeOption(stringResource(R.string.theme_system), AppSettings.THEME_SYSTEM, settings.themeMode) {
                        vm.setThemeMode(it)
                        showThemeDialog = false
                    }
                    ThemeOption(stringResource(R.string.theme_light), AppSettings.THEME_LIGHT, settings.themeMode) {
                        vm.setThemeMode(it)
                        showThemeDialog = false
                    }
                    ThemeOption(stringResource(R.string.theme_dark), AppSettings.THEME_DARK, settings.themeMode) {
                        vm.setThemeMode(it)
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    ThemeOption(stringResource(R.string.lang_system), "system", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke(null)
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_zh), "zh", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("zh")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_en), "en", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("en")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_es), "es", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("es")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_ja), "ja", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("ja")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_ko), "ko", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("ko")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_de), "de", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("de")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_ru), "ru", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("ru")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_fr), "fr", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("fr")
                        showLanguageDialog = false
                    }
                    ThemeOption(stringResource(R.string.lang_hi), "hi", com.safekey.authenticator.data.LanguagePrefs.get(context) ?: "system") {
                        onLanguageChanged?.invoke("hi")
                        showLanguageDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showDestroyModeDialog) {
        AlertDialog(
            onDismissRequest = { showDestroyModeDialog = false },
            title = { Text(stringResource(R.string.destroy_mode)) },
            text = {
                Column {
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
            onDismissRequest = { showOffsetDialog = false },
            title = { Text(stringResource(R.string.time_offset)) },
            text = {
                Column {
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
            onDismissRequest = { showThresholdDialog = false },
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
