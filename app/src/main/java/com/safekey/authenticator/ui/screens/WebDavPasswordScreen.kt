package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.DialogBodyText
import com.safekey.authenticator.ui.components.SettingRow
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.scrollBelowTopBar

/**
 * Third-level page under WebDAV backup: the export password that protects
 * uploaded backups.
 *
 * Manual backups used to ask for a fresh password on every run. The password
 * can now be remembered once (the checkbox in the backup dialog) and managed
 * here: replace it, or forget it so the app asks again.
 *
 * The password is only ever stored encrypted with the Android Keystore key —
 * the page never shows it back, because showing it would put the plaintext on
 * screen and in screenshots for no benefit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavPasswordScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()

    var showSetDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SimpleTopBar(title = stringResource(R.string.webdav_password_title), onBack = onBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scrollBelowTopBar(padding)
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.webdav_password_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SettingRow(
                icon = AppIcons.VpnKey,
                title = stringResource(R.string.webdav_password_title),
                description = stringResource(
                    if (settings.webDavBackupPasswordSet) R.string.webdav_password_row_set_desc
                    else R.string.webdav_password_row_unset_desc
                ),
                onClick = { showSetDialog = true },
                trailing = {
                    Text(
                        text = stringResource(
                            if (settings.webDavBackupPasswordSet) R.string.webdav_password_remembered_short
                            else R.string.webdav_password_unset_short
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            if (settings.webDavBackupPasswordSet) {
                SettingRow(
                    icon = AppIcons.Delete,
                    title = stringResource(R.string.webdav_password_clear),
                    onClick = { showClearConfirm = true }
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.webdav_password_storage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
    }

    if (showSetDialog) {
        BackupPasswordDialog(
            onDismiss = { showSetDialog = false },
            onConfirm = { password, _ ->
                showSetDialog = false
                // Stored unconditionally here: this page exists to manage the
                // remembered password, so a "remember" checkbox would be noise.
                vm.setWebDavBackupPassword(password)
                vm.showToast(context.getString(R.string.webdav_password_saved))
            },
            titleRes = if (settings.webDavBackupPasswordSet) R.string.webdav_password_change_title
            else R.string.webdav_password_set_title,
            descriptionRes = R.string.webdav_password_set_desc,
            showRemember = false
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.webdav_password_clear_confirm_title)) },
            text = { DialogBodyText(stringResource(R.string.webdav_password_clear_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        vm.setWebDavBackupPassword("")
                        vm.showToast(context.getString(R.string.webdav_password_cleared))
                    }
                ) { Text(stringResource(R.string.webdav_password_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
