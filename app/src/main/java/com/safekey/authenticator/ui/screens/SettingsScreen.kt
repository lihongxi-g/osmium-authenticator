package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBiometricChanged: ((Boolean) -> Unit)? = null
) {
    val settings by vm.settings.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }

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

            SectionHeader(stringResource(R.string.settings_security))

            SettingRow(
                icon = AppIcons.Security,
                title = stringResource(R.string.biometric_lock),
                description = stringResource(R.string.biometric_lock_desc),
                trailing = {
                    Switch(
                        checked = settings.biometricLock,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Verify identity before enabling the lock
                                if (onBiometricChanged != null) {
                                    onBiometricChanged(true)
                                }
                            } else {
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
                icon = Icons.Filled.Info,
                title = stringResource(R.string.version),
                trailing = {
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
}

@Composable
private fun ThemeOption(label: String, value: Int, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
