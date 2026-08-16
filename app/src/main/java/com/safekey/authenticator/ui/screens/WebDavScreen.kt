package com.safekey.authenticator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.data.WebDavServerConfig
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.network.WebDavClient
import com.safekey.authenticator.network.WebDavException
import com.safekey.authenticator.network.WebDavFile
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WebDAV backup: configure a server on the local network, upload the
 * password-encrypted export, and restore one of the backups stored there.
 *
 * The app connects ONLY to the address the user types here — never anywhere
 * else. Backups are encrypted before they leave the device (see VaultIO).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavScreen(
    vm: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedConfig by vm.webDavConfig.collectAsState()

    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var seeded by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    var busyLabel by remember { mutableStateOf<String?>(null) }

    // dialogs / flows
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestorePassword by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerFiles by remember { mutableStateOf<List<WebDavFile>>(emptyList()) }
    var restoreVault by remember { mutableStateOf<VaultFile?>(null) }
    // Export password held across the two restore dialogs (password → picker).
    var pendingRestorePassword by remember { mutableStateOf("") }
    // backup management (list + delete)
    var showManage by remember { mutableStateOf(false) }
    var manageFiles by remember { mutableStateOf<List<WebDavFile>>(emptyList()) }
    var deleteTarget by remember { mutableStateOf<WebDavFile?>(null) }

    // Seed the form from the saved config once (and only once, so the user's
    // edits are never overwritten by a re-emission of the flow).
    LaunchedEffect(savedConfig) {
        val cfg = savedConfig
        if (!seeded && cfg != null) {
            url = cfg.baseUrl
            username = cfg.username
            password = cfg.password
            seeded = true
        }
    }

    fun currentConfig() = WebDavServerConfig(
        baseUrl = url.trim(),
        username = username.trim(),
        password = password
    )

    fun validateUrl(): Boolean {
        val ok = url.isNotBlank()
        urlError = !ok
        return ok
    }

    fun saveConfig() {
        if (url.isBlank()) return
        vm.saveWebDavConfig(currentConfig())
        vm.showToast(context.getString(R.string.webdav_saved))
    }

    fun runBusy(label: String, block: suspend () -> Unit) {
        scope.launch {
            busyLabel = label
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: WebDavException) {
                vm.showToast(e.message ?: context.getString(R.string.export_failed, "WebDAV"))
            } catch (e: VaultFormatException) {
                vm.showToast(
                    context.getString(
                        if (e.wrongPassword) R.string.error_import_wrong_password
                        else R.string.error_import_format
                    )
                )
            } catch (e: Exception) {
                vm.showToast(e.message ?: "Error")
            }
            busyLabel = null
        }
    }

    fun startRestore(file: WebDavFile, exportPassword: String) {
        runBusy(context.getString(R.string.webdav_downloading)) {
            val bytes = WebDavClient.download(currentConfig(), file.href)
            val vault = withContext(Dispatchers.Default) {
                VaultIO.decrypt(bytes, exportPassword.toCharArray())
            }
            restoreVault = vault
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.webdav_title), onBack = onBack) }
    ) { padding ->
        val currentVault = restoreVault
        if (currentVault != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                VaultImportFlow(
                    vm = vm,
                    vault = currentVault,
                    onDone = { restoreVault = null },
                    onBackToPassword = { restoreVault = null }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.webdav_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; urlError = false },
                    label = { Text(stringResource(R.string.webdav_url_label)) },
                    placeholder = { Text(stringResource(R.string.webdav_url_hint)) },
                    singleLine = true,
                    isError = urlError,
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.webdav_username_label)) },
                    singleLine = true,
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.webdav_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (urlError) {
                    Text(
                        text = stringResource(R.string.webdav_error_url),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (!validateUrl()) return@OutlinedButton
                            runBusy(context.getString(R.string.webdav_testing)) {
                                WebDavClient.testConnection(currentConfig())
                                withContext(Dispatchers.Main) {
                                    vm.showToast(context.getString(R.string.webdav_test_ok))
                                }
                            }
                        },
                        enabled = busyLabel == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.webdav_test))
                    }
                    TextButton(
                        onClick = { saveConfig() },
                        enabled = busyLabel == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.webdav_save))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { if (validateUrl()) showBackupDialog = true },
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.webdav_backup_now))
                }
                OutlinedButton(
                    onClick = { if (validateUrl()) showRestorePassword = true },
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.webdav_restore))
                }
                OutlinedButton(
                    onClick = {
                        if (validateUrl()) {
                            showManage = true
                            runBusy(context.getString(R.string.webdav_listing)) {
                                manageFiles = WebDavClient.listBackups(currentConfig())
                            }
                        }
                    },
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.webdav_manage))
                }
                busyLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.webdav_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---------------------------------------------------- backup password

    if (showBackupDialog) {
        BackupPasswordDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { exportPassword ->
                showBackupDialog = false
                if (!validateUrl()) return@BackupPasswordDialog
                saveConfig()
                val fileName = "osmium-backup-${
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                }.json"
                runBusy(context.getString(R.string.webdav_uploading)) {
                    val repo = (context.applicationContext as com.safekey.authenticator.SafeKeyApp).accountRepository
                    val pinHash = vm.pinManager.getPinHashForExport()
                    val vf = repo.exportVault(
                        pinSalt = pinHash?.first ?: "",
                        pinHash = pinHash?.second ?: ""
                    )
                    val payload = VaultIO.encrypt(vf, exportPassword.toCharArray())
                    try {
                        WebDavClient.upload(currentConfig(), fileName, payload.toByteArray(Charsets.UTF_8))
                    } catch (e: WebDavException) {
                        throw WebDavException(
                            context.getString(R.string.webdav_backup_failed, e.message ?: ""), e)
                    }
                    withContext(Dispatchers.Main) {
                        vm.showToast(context.getString(R.string.webdav_backup_done))
                    }
                }
            }
        )
    }

    // ---------------------------------------------------- restore password

    if (showRestorePassword) {
        RestorePasswordDialog(
            onDismiss = { showRestorePassword = false },
            onConfirm = { exportPassword ->
                showRestorePassword = false
                if (!validateUrl()) return@RestorePasswordDialog
                runBusy(context.getString(R.string.webdav_listing)) {
                    val files = WebDavClient.listBackups(currentConfig())
                    if (files.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            vm.showToast(context.getString(R.string.webdav_no_backups))
                        }
                        return@runBusy
                    }
                    pickerFiles = files
                    showPicker = true
                }
                // password is kept for the download step
                pendingRestorePassword = exportPassword
            }
        )
    }

    // ------------------------------------------------------------ picker

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.webdav_pick_file)) },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                ) {
                    pickerFiles.forEach { file ->
                        TextButton(
                            onClick = {
                                showPicker = false
                                startRestore(file, pendingRestorePassword)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${file.name}  ·  ${formatBackupDate(file.lastModified)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ------------------------------------------------------ manage & delete

    if (showManage) {
        AlertDialog(
            onDismissRequest = { showManage = false },
            title = { Text(stringResource(R.string.webdav_manage)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    if (manageFiles.isEmpty()) {
                        Text(
                            text = stringResource(R.string.webdav_no_backups),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        manageFiles.forEach { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${file.name}  ·  ${formatBackupDate(file.lastModified)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { deleteTarget = file }) {
                                    Icon(
                                        imageVector = AppIcons.Delete,
                                        contentDescription = stringResource(R.string.webdav_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManage = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.webdav_delete_confirm_title)) },
            text = { Text(stringResource(R.string.webdav_delete_confirm_msg, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteTarget = null
                        runBusy(context.getString(R.string.webdav_deleting)) {
                            WebDavClient.delete(currentConfig(), target.href)
                            manageFiles = WebDavClient.listBackups(currentConfig())
                            withContext(Dispatchers.Main) {
                                vm.showToast(context.getString(R.string.webdav_deleted))
                            }
                        }
                    }
                ) { Text(stringResource(R.string.webdav_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun BackupPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.webdav_backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.webdav_backup_password_desc),
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
                    error = when {
                        password.isEmpty() -> context.getString(R.string.error_password_empty)
                        password.length < 8 -> context.getString(R.string.error_password_weak)
                        password != confirm -> context.getString(R.string.error_password_mismatch)
                        else -> null
                    }
                    if (error == null) onConfirm(password)
                }
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun RestorePasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.webdav_restore_password_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.export_password_hint)) },
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
                    if (password.isEmpty()) {
                        error = context.getString(R.string.error_password_empty)
                    } else {
                        onConfirm(password)
                    }
                }
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

// ------------------------------------------------------- state & helpers

private fun formatBackupDate(epochMillis: Long): String =
    if (epochMillis <= 0L) ""
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMillis))
