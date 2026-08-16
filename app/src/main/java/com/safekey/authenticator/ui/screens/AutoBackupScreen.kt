package com.safekey.authenticator.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.backup.AutoBackupScheduler
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Scheduled automatic backup: pick a target (WebDAV server or the phone's
 * Download/Osmium folder), an interval in days and a time of day. The next
 * run and the last result are shown with an explicit GMT+8 timezone label.
 *
 * The backup itself runs unattended in [com.safekey.authenticator.backup.AutoBackupWorker]
 * — the export password is stored encrypted with the Android Keystore key
 * when the user enables this feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoBackupScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val webDavConfig by vm.webDavConfig.collectAsState()

    var showTargetDialog by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }
    var showKeepDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var pendingEnableAfterPassword by remember { mutableStateOf(false) }

    // React to every schedule-affecting setting change.
    LaunchedEffect(
        settings.autoBackupEnabled,
        settings.autoBackupHour,
        settings.autoBackupMinute,
        settings.autoBackupIntervalDays
    ) {
        AutoBackupScheduler.schedule(context, settings)
    }

    // Android 8/9 needs WRITE_EXTERNAL_STORAGE for the public folder.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, the screen reflects reality via checkSelfPermission */ }

    fun hasLegacyPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    fun needsLegacyPermission(): Boolean =
        Build.VERSION.SDK_INT <= 28 &&
            settings.autoBackupTarget == AppSettings.AUTO_BACKUP_TARGET_LOCAL

    fun continueEnable() {
        if (settings.autoBackupTarget == AppSettings.AUTO_BACKUP_TARGET_WEBDAV &&
            webDavConfig == null
        ) {
            vm.showToast(context.getString(R.string.auto_backup_webdav_missing))
            vm.nav.push(Screen.WebDav)
            return
        }
        if (needsLegacyPermission() && !hasLegacyPermission()) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        vm.setAutoBackupEnabled(true)
    }

    fun onToggleEnable(enable: Boolean) {
        if (!enable) {
            vm.setAutoBackupEnabled(false)
            return
        }
        if (!settings.autoBackupPasswordSet) {
            pendingEnableAfterPassword = true
            showPasswordDialog = true
            return
        }
        continueEnable()
    }

    fun onRunNow() {
        if (!settings.autoBackupPasswordSet) {
            vm.showToast(context.getString(R.string.auto_backup_need_password))
            showPasswordDialog = true
            return
        }
        if (settings.autoBackupTarget == AppSettings.AUTO_BACKUP_TARGET_WEBDAV &&
            webDavConfig == null
        ) {
            vm.showToast(context.getString(R.string.auto_backup_webdav_missing))
            vm.nav.push(Screen.WebDav)
            return
        }
        AutoBackupScheduler.runNow(context)
        vm.showToast(context.getString(R.string.auto_backup_started))
    }

    fun formatWithTz(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        fmt.timeZone = TimeZone.getTimeZone("GMT+8")
        return fmt.format(Date(millis)) + " " +
            context.getString(R.string.auto_backup_tz_label)
    }

    val nextRun = if (settings.autoBackupEnabled) {
        AutoBackupScheduler.nextRunMillis(
            now = System.currentTimeMillis(),
            hour = settings.autoBackupHour,
            minute = settings.autoBackupMinute,
            intervalDays = settings.autoBackupIntervalDays
        )
    } else null

    val timePickerState = remember(
        settings.autoBackupHour, settings.autoBackupMinute
    ) {
        TimePickerState(
            initialHour = settings.autoBackupHour,
            initialMinute = settings.autoBackupMinute,
            is24Hour = true
        )
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.auto_backup_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingRow(
                icon = AppIcons.Timer,
                title = stringResource(R.string.auto_backup_title),
                description = stringResource(R.string.auto_backup_desc),
                trailing = {
                    Switch(
                        checked = settings.autoBackupEnabled,
                        onCheckedChange = { onToggleEnable(it) }
                    )
                }
            )

            if (settings.autoBackupEnabled) {
                SettingRow(
                    icon = AppIcons.Dns,
                    title = stringResource(R.string.auto_backup_target_label),
                    trailing = {
                        Text(
                            text = when (settings.autoBackupTarget) {
                                AppSettings.AUTO_BACKUP_TARGET_LOCAL ->
                                    stringResource(R.string.auto_backup_target_local)
                                else ->
                                    stringResource(R.string.auto_backup_target_webdav)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showTargetDialog = true }
                )

                SettingRow(
                    icon = AppIcons.Timer,
                    title = stringResource(R.string.auto_backup_interval_label),
                    trailing = {
                        Text(
                            text = if (settings.autoBackupIntervalDays <= 1)
                                stringResource(R.string.auto_backup_interval_day)
                            else
                                stringResource(
                                    R.string.auto_backup_interval_every_days,
                                    settings.autoBackupIntervalDays
                                ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showIntervalDialog = true }
                )

                SettingRow(
                    icon = AppIcons.Timer,
                    title = stringResource(R.string.auto_backup_time_label),
                    trailing = {
                        Text(
                            text = String.format(
                                Locale.getDefault(), "%02d:%02d",
                                settings.autoBackupHour, settings.autoBackupMinute
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showTimeDialog = true }
                )

                SettingRow(
                    icon = AppIcons.VpnKey,
                    title = stringResource(R.string.auto_backup_password_label),
                    description = stringResource(R.string.auto_backup_password_desc),
                    trailing = {
                        Text(
                            text = if (settings.autoBackupPasswordSet)
                                stringResource(R.string.auto_backup_password_set)
                            else
                                stringResource(R.string.auto_backup_password_missing),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showPasswordDialog = true }
                )

                SettingRow(
                    icon = AppIcons.FileUpload,
                    title = stringResource(R.string.auto_backup_keep_label),
                    description = stringResource(R.string.auto_backup_keep_desc),
                    trailing = {
                        Text(
                            text = settings.autoBackupKeepCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showKeepDialog = true }
                )

                // ---- status block

                nextRun?.let { millis ->
                    Text(
                        text = stringResource(R.string.auto_backup_next_run, formatWithTz(millis)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }

                Text(
                    text = if (settings.autoBackupLastTime == 0L) {
                        stringResource(R.string.auto_backup_never_run)
                    } else if (settings.autoBackupLastError.isEmpty()) {
                        stringResource(
                            R.string.auto_backup_last_success,
                            formatWithTz(settings.autoBackupLastTime)
                        )
                    } else {
                        stringResource(
                            R.string.auto_backup_last_failed,
                            formatWithTz(settings.autoBackupLastTime),
                            settings.autoBackupLastError
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )

                if (settings.autoBackupTarget == AppSettings.AUTO_BACKUP_TARGET_LOCAL) {
                    Text(
                        text = stringResource(R.string.auto_backup_local_dir_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                    if (needsLegacyPermission() && !hasLegacyPermission()) {
                        Text(
                            text = stringResource(R.string.auto_backup_local_permission_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable {
                                    permissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }
                }

                Button(
                    onClick = { onRunNow() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.auto_backup_run_now))
                }
            }
        }
    }

    // ------------------------------------------------------------ dialogs

    if (showTargetDialog) {
        AlertDialog(
            onDismissRequest = { showTargetDialog = false },
            title = { Text(stringResource(R.string.auto_backup_target_label)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showTargetDialog = false
                            vm.setAutoBackupTarget(AppSettings.AUTO_BACKUP_TARGET_WEBDAV)
                            if (settings.autoBackupEnabled && webDavConfig == null) {
                                vm.showToast(
                                    context.getString(R.string.auto_backup_webdav_missing)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.auto_backup_target_webdav)) }
                    TextButton(
                        onClick = {
                            showTargetDialog = false
                            vm.setAutoBackupTarget(AppSettings.AUTO_BACKUP_TARGET_LOCAL)
                            if (needsLegacyPermission() && !hasLegacyPermission() &&
                                settings.autoBackupEnabled
                            ) {
                                permissionLauncher.launch(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.auto_backup_target_local)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTargetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showIntervalDialog) {
        AlertDialog(
            onDismissRequest = { showIntervalDialog = false },
            title = { Text(stringResource(R.string.auto_backup_interval_picker_title)) },
            text = {
                Column {
                    listOf(1, 2, 3, 7, 14, 30).forEach { days ->
                        TextButton(
                            onClick = {
                                showIntervalDialog = false
                                vm.setAutoBackupIntervalDays(days)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (days == 1)
                                    stringResource(R.string.auto_backup_interval_day)
                                else
                                    stringResource(
                                        R.string.auto_backup_interval_every_days, days
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showIntervalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showKeepDialog) {
        AlertDialog(
            onDismissRequest = { showKeepDialog = false },
            title = { Text(stringResource(R.string.auto_backup_keep_picker_title)) },
            text = {
                Column {
                    (1..AppSettings.AUTO_BACKUP_KEEP_MAX).forEach { count ->
                        TextButton(
                            onClick = {
                                showKeepDialog = false
                                vm.setAutoBackupKeepCount(count)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(count.toString()) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showKeepDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showTimeDialog) {
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text(stringResource(R.string.auto_backup_time_picker_title)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showTimeDialog = false
                        vm.setAutoBackupTime(
                            timePickerState.hour,
                            timePickerState.minute
                        )
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showPasswordDialog) {
        AutoBackupPasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                showPasswordDialog = false
                vm.setAutoBackupPassword(password)
                vm.showToast(context.getString(R.string.auto_backup_password_saved))
                if (pendingEnableAfterPassword) {
                    pendingEnableAfterPassword = false
                    continueEnable()
                }
            }
        )
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun AutoBackupPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val emptyError = stringResource(R.string.error_password_empty)
    val mismatchError = stringResource(R.string.error_password_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_backup_password_label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auto_backup_password_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.export_password_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(stringResource(R.string.export_confirm_hint)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        password.isEmpty() -> error = emptyError
                        password != confirm -> error = mismatchError
                        else -> onConfirm(password)
                    }
                }
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
