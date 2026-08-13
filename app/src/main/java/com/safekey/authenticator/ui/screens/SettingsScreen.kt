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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SectionHeader
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.navigation.Screen
import com.safekey.authenticator.ui.theme.themePresets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onOpenPinSetup: () -> Unit,
    onOpenPinVerify: () -> Unit,
    onBiometricChanged: ((Boolean) -> Unit)? = null,
    onLanguageChanged: ((String?) -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val context = LocalContext.current
    val hasPin = vm.hasLocalPin()
    val hasDestroyPin = vm.pinManager.hasDestroyPin()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showPinVerifyModeDialog by remember { mutableStateOf(false) }
    var showPinTimeDialog by remember { mutableStateOf(false) }
    var showDestroyModeDialog by remember { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var showClearPinDialog by remember { mutableStateOf(false) }

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
                title = stringResource(R.string.theme_color),
                description = stringResource(R.string.theme_color_desc),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    themePresets.getOrElse(settings.themeColorIndex) { themePresets[3] }.color
                                )
                        )
                        Text(
                            text = stringResource(
                                themePresets.getOrElse(settings.themeColorIndex) { themePresets[3] }.nameResKey
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                        )
                    }
                },
                onClick = { showColorDialog = true }
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
                icon = AppIcons.ContentPaste,
                title = stringResource(R.string.language),
                trailing = {
                    Text(
                        text = when (com.safekey.authenticator.data.LanguagePrefs.get(context)) {
                            "zh" -> stringResource(R.string.lang_zh)
                            "en" -> stringResource(R.string.lang_en)
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
                icon = AppIcons.Security,
                title = stringResource(R.string.biometric_lock),
                description = stringResource(R.string.biometric_lock_desc),
                trailing = {
                    Switch(
                        checked = settings.biometricLock,
                        onCheckedChange = { checked ->
                            if (checked && onBiometricChanged != null) {
                                onBiometricChanged(true)
                            } else if (!checked) {
                                vm.setBiometricLock(false)
                            }
                        }
                    )
                }
            )

            SettingRow(
                icon = AppIcons.Timer,
                title = stringResource(R.string.clipboard_timeout),
                trailing = {
                    Text(
                        text = when (settings.clipboardClearSeconds) {
                            0 -> stringResource(R.string.clipboard_off)
                            15 -> stringResource(R.string.clipboard_15s)
                            60 -> stringResource(R.string.clipboard_60s)
                            else -> stringResource(R.string.clipboard_30s)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = { showClipboardDialog = true }
            )

            // -------------------------------------------------------- PIN

            SectionHeader(stringResource(R.string.settings_pin))

            SettingRow(
                icon = AppIcons.Keyboard,
                title = if (hasPin) stringResource(R.string.app_pin_manage) else stringResource(R.string.app_pin_setup),
                description = stringResource(R.string.app_pin_desc),
                onClick = {
                    if (hasPin) onOpenPinVerify() else onOpenPinSetup()
                }
            )

            SettingRow(
                icon = AppIcons.Timer,
                title = stringResource(R.string.pin_periodic_verify),
                description = stringResource(R.string.pin_periodic_desc),
                trailing = {
                    Text(
                        text = when (settings.pinVerifyMode) {
                            AppSettings.PIN_VERIFY_RANDOM -> stringResource(R.string.pin_mode_random)
                            AppSettings.PIN_VERIFY_DAILY -> stringResource(R.string.pin_mode_daily)
                            else -> stringResource(R.string.clipboard_off)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    if (!hasPin) {
                        vm.showToast(context.getString(R.string.pin_need_setup_first))
                    } else {
                        showPinVerifyModeDialog = true
                    }
                }
            )

            if (settings.pinVerifyMode == AppSettings.PIN_VERIFY_DAILY) {
                SettingRow(
                    icon = AppIcons.Timer,
                    title = stringResource(R.string.pin_daily_time),
                    trailing = {
                        Text(
                            text = String.format("%02d:%02d", settings.pinFixedHour, settings.pinFixedMinute),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showPinTimeDialog = true }
                )
            }

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
                    icon = AppIcons.Keyboard,
                    title = if (hasDestroyPin) stringResource(R.string.destroy_pin_manage)
                    else stringResource(R.string.destroy_pin_setup),
                    description = stringResource(R.string.destroy_pin_desc),
                    onClick = {
                        vm.nav.push(Screen.PinSetup("destroy_pin"))
                    }
                )
            }

            if (settings.destroyMode == AppSettings.DESTROY_FAIL_COUNT) {
                SettingRow(
                    icon = AppIcons.Warning,
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

            if (hasPin && settings.pinVerifyMode == AppSettings.PIN_VERIFY_OFF) {
                Text(
                    text = stringResource(R.string.pin_clear_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { showClearPinDialog = true }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            SectionHeader(stringResource(R.string.settings_data))

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
                icon = AppIcons.SwapVert,
                title = stringResource(R.string.accounts_count, accounts.size)
            )

            SectionHeader(stringResource(R.string.settings_about))

            SettingRow(
                icon = AppIcons.Info,
                title = stringResource(R.string.version),
                description = stringResource(R.string.version_click_hint),
                trailing = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                onClick = {
                    val subject = Uri.encode("SafeKey v${BuildConfig.VERSION_NAME} Feedback")
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:zhif0776@hotmail.com?subject=$subject")
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        vm.showToast(context.getString(R.string.no_email_app))
                    }
                }
            )

            Text(
                text = stringResource(R.string.about_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)
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

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text(stringResource(R.string.theme_color)) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                    themePresets.forEachIndexed { index, preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setThemeColorIndex(index)
                                    showColorDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                            )
                            Text(
                                text = stringResource(preset.nameResKey),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            )
                            if (settings.themeColorIndex == index) {
                                androidx.compose.material3.Icon(
                                    imageVector = AppIcons.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showColorDialog = false }) { Text(stringResource(R.string.close)) }
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
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showClipboardDialog) {
        AlertDialog(
            onDismissRequest = { showClipboardDialog = false },
            title = { Text(stringResource(R.string.clipboard_timeout)) },
            text = {
                Column {
                    ThemeOption(stringResource(R.string.clipboard_off), 0, settings.clipboardClearSeconds) {
                        vm.setClipboardClearSeconds(it)
                        showClipboardDialog = false
                    }
                    ThemeOption(stringResource(R.string.clipboard_15s), 15, settings.clipboardClearSeconds) {
                        vm.setClipboardClearSeconds(it)
                        showClipboardDialog = false
                    }
                    ThemeOption(stringResource(R.string.clipboard_30s), 30, settings.clipboardClearSeconds) {
                        vm.setClipboardClearSeconds(it)
                        showClipboardDialog = false
                    }
                    ThemeOption(stringResource(R.string.clipboard_60s), 60, settings.clipboardClearSeconds) {
                        vm.setClipboardClearSeconds(it)
                        showClipboardDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showClipboardDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showPinVerifyModeDialog) {
        AlertDialog(
            onDismissRequest = { showPinVerifyModeDialog = false },
            title = { Text(stringResource(R.string.pin_periodic_verify)) },
            text = {
                Column {
                    ThemeOption(stringResource(R.string.clipboard_off), AppSettings.PIN_VERIFY_OFF, settings.pinVerifyMode) {
                        vm.setPinVerifyMode(it)
                        showPinVerifyModeDialog = false
                    }
                    ThemeOption(stringResource(R.string.pin_mode_random), AppSettings.PIN_VERIFY_RANDOM, settings.pinVerifyMode) {
                        vm.setPinVerifyMode(it)
                        showPinVerifyModeDialog = false
                    }
                    ThemeOption(stringResource(R.string.pin_mode_daily), AppSettings.PIN_VERIFY_DAILY, settings.pinVerifyMode) {
                        vm.setPinVerifyMode(it)
                        showPinVerifyModeDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPinVerifyModeDialog = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }

    if (showPinTimeDialog) {
        var hourText by remember { mutableStateOf(settings.pinFixedHour.toString()) }
        var minuteText by remember { mutableStateOf(settings.pinFixedMinute.toString()) }
        AlertDialog(
            onDismissRequest = { showPinTimeDialog = false },
            title = { Text(stringResource(R.string.pin_daily_time)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { hourText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.pin_time_hour)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { minuteText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text(stringResource(R.string.pin_time_minute)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val h = (hourText.toIntOrNull() ?: 0).coerceIn(0, 23)
                    val m = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                    vm.setPinFixedTime(h, m)
                    showPinTimeDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPinTimeDialog = false }) { Text(stringResource(R.string.cancel)) }
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

    if (showClearPinDialog) {
        AlertDialog(
            onDismissRequest = { showClearPinDialog = false },
            title = { Text(stringResource(R.string.pin_clear_title)) },
            text = { Text(stringResource(R.string.pin_clear_message)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAppPin()
                    showClearPinDialog = false
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearPinDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
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
