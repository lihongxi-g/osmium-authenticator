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
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.safekey.authenticator.network.LocalNetworkAccess
import com.safekey.authenticator.network.LocalNetworkPermissionException
import com.safekey.authenticator.network.WebDavClient
import com.safekey.authenticator.network.WebDavException
import com.safekey.authenticator.network.WebDavFile
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.DialogBodyText
import com.safekey.authenticator.ui.components.LocalNetworkPermissionGate
import com.safekey.authenticator.ui.components.rememberLocalNetworkPermissionRequest
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.scrollBelowTopBar
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
    onBack: () -> Unit,
    onManagePassword: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedConfig by vm.webDavConfig.collectAsState()
    val settings by vm.settings.collectAsState()

    // The form fields use rememberSaveable, not remember: with "verify on
    // open" enabled (the default) going to the background locks the app, and
    // the lock gate replaces the whole nav host — which destroys this
    // composable. Plain remember state died there, so a user who switched to
    // another app to copy the server address and password came back to an
    // empty form. The SaveableStateProvider in MainNavHost keeps these values.
    var url by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    // SHA-256 of the certificate the user trusted for this server. Public
    // data (not a secret), so rememberSaveable is fine: it survives the
    // lock-gate rebuild exactly like the other form fields.
    var certPin by rememberSaveable { mutableStateOf("") }
    var seeded by rememberSaveable { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    // Deliberately NOT saveable: a restored non-null busyLabel would leave the
    // whole form disabled forever (the coroutine that clears it is gone).
    var busyLabel by remember { mutableStateOf<String?>(null) }
    var localNetworkDenied by remember { mutableStateOf(false) }
    val requestLocalNetwork = rememberLocalNetworkPermissionRequest {
        localNetworkDenied = true
        vm.showToast(LocalNetworkAccess.deniedMessage(context))
    }

    // dialogs / flows
    var showBackupDialog by remember { mutableStateOf(false) }
    var showHttpWarning by remember { mutableStateOf(false) }
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
    // Fingerprint waiting for the user's confirmation (null = no prompt).
    var certPrompt by remember { mutableStateOf<String?>(null) }
    // What to run once the one-time plaintext warning is accepted (a backup
    // that needs the config saved first, or null for the Save button).
    var pendingAfterHttpAck by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Seed the form from the saved config once (and only once, so the user's
    // edits are never overwritten by a re-emission of the flow).
    LaunchedEffect(savedConfig) {
        val cfg = savedConfig
        if (!seeded && cfg != null) {
            url = cfg.baseUrl
            username = cfg.username
            password = cfg.password
            certPin = cfg.trustedCertSha256
            seeded = true
        }
    }

    fun currentConfig() = WebDavServerConfig(
        baseUrl = url.trim(),
        username = username.trim(),
        password = password,
        trustedCertSha256 = certPin
    )

    fun validateUrl(): Boolean {
        val ok = url.isNotBlank()
        urlError = !ok
        return ok
    }

    fun persistConfig() {
        vm.saveWebDavConfig(currentConfig())
    }

    /**
     * Plaintext HTTP is acknowledged **once**, then never asked again.
     *
     * Before this, every save raised the dialog — and a backup saves the form
     * on every run, so the warning came back on every upload even though the
     * user had accepted the exact same LAN address minutes earlier.
     */
    fun httpAckPending(): Boolean =
        url.trim().startsWith("http://", ignoreCase = true) &&
            !settings.webDavHttpWarningAcknowledged

    /**
     * Persist the form, then run [then].
     *
     * [silent] is for the save a backup performs by itself: the "saved" toast
     * would otherwise land on top of the upload result every single time.
     */
    fun saveConfig(silent: Boolean = false, then: (() -> Unit)? = null) {
        if (url.isBlank()) {
            urlError = true
            return
        }
        if (httpAckPending()) {
            pendingAfterHttpAck = then
            showHttpWarning = true
            return
        }
        persistConfig()
        if (!silent) vm.showToast(context.getString(R.string.webdav_saved))
        then?.invoke()
    }

    fun runBusy(label: String, block: suspend () -> Unit) {
        scope.launch {
            busyLabel = label
            try {
                withContext(Dispatchers.IO) {
                    LocalNetworkAccess.checkWebDav(context, currentConfig().baseUrl)
                    block()
                }
            } catch (_: LocalNetworkPermissionException) {
                requestLocalNetwork { runBusy(label, block) }
            } catch (e: WebDavException) {
                vm.showToast(e.message ?: context.getString(R.string.export_failed, "WebDAV"))
            } catch (e: VaultFormatException) {
                vm.showToast(
                    context.getString(
                        if (e.wrongPassword) R.string.error_import_wrong_password
                        else R.string.error_import_format
                    )
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                vm.showToast(e.message ?: "Error")
            } finally {
                busyLabel = null
            }
        }
    }

    // Reads the certificate the server presents and shows the fingerprint for
    // confirmation. No request and no credentials are sent for this.
    fun fetchCertificate() {
        if (!validateUrl()) return
        runBusy(context.getString(R.string.webdav_cert_fetching)) {
            val sha256 = WebDavClient.serverCertificateSha256(currentConfig())
            withContext(Dispatchers.Main) { certPrompt = sha256 }
        }
    }

    /**
     * Uploads one backup, encrypted with [exportPassword].
     *
     * Split out of [startBackup] because Kotlin local functions cannot be
     * referenced before they are declared: the caller has to come last.
     */
    fun uploadBackup(exportPassword: String) {
        val fileName = "osmium-backup-${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        }.json"
        runBusy(context.getString(R.string.webdav_uploading)) {
            val repo = (context.applicationContext as com.safekey.authenticator.SafeKeyApp).accountRepository
            val pinHash = vm.pinManager.getPinHashForExport()
            val export = repo.exportVault(
                pinSalt = pinHash?.first ?: "",
                pinHash = pinHash?.second ?: ""
            )
            if (export.dropped > 0) {
                throw WebDavException(
                    "${export.dropped} accounts could not be decrypted — upload aborted"
                )
            }
            val payload = VaultIO.encrypt(export.vault, exportPassword.toCharArray())
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

    /**
     * Backup entry point shared by both password sources: the one the user just
     * typed (and maybe asked us to remember), and the remembered one that lets
     * "Back up now" skip the dialog entirely.
     */
    fun startBackup(exportPassword: String) {
        if (!validateUrl()) return
        // Silent: a backup persists the form, it is not a "Save settings" action.
        saveConfig(silent = true) { uploadBackup(exportPassword) }
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
                    .scrollBelowTopBar(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (localNetworkDenied) {
                    LocalNetworkPermissionGate { }
                }
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
                if (url.trim().startsWith("https://", ignoreCase = true)) {
                    if (certPin.isBlank()) {
                        TextButton(
                            onClick = { fetchCertificate() },
                            enabled = busyLabel == null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.webdav_cert_trust_button))
                        }
                    } else {
                        Text(
                            text = stringResource(
                                R.string.webdav_cert_pinned,
                                WebDavClient.formatFingerprint(certPin)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                certPin = ""
                                vm.saveWebDavConfig(currentConfig())
                                vm.showToast(context.getString(R.string.webdav_cert_reset_done))
                            },
                            enabled = busyLabel == null
                        ) {
                            Text(stringResource(R.string.webdav_cert_reset))
                        }
                    }
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
                    onClick = {
                        if (!validateUrl()) return@Button
                        // A remembered password means no dialog at all — that is
                        // the point of remembering it. Read it off the main
                        // thread (DataStore + Keystore decryption).
                        scope.launch {
                            val remembered = try {
                                withContext(Dispatchers.IO) { vm.webDavBackupPassword() }
                            } catch (e: Exception) {
                                null
                            }
                            if (remembered != null) {
                                startBackup(remembered)
                            } else {
                                showBackupDialog = true
                            }
                        }
                    },
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
                OutlinedButton(
                    onClick = { onManagePassword() },
                    enabled = busyLabel == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.webdav_password_title))
                }
                Text(
                    text = stringResource(
                        if (settings.webDavBackupPasswordSet) R.string.webdav_password_state_remembered
                        else R.string.webdav_password_state_none
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

    // ------------------------------------------------ plaintext HTTP warning

    if (showHttpWarning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.webdav_http_warning_title)) },
            text = { DialogBodyText(stringResource(R.string.webdav_http_warning_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHttpWarning = false
                        // Accepting is permanent. The dialog exists so the user
                        // thinks about plaintext once, not to nag on every save.
                        vm.setWebDavHttpWarningAcknowledged()
                        val after = pendingAfterHttpAck
                        pendingAfterHttpAck = null
                        persistConfig()
                        if (after != null) {
                            after()
                        } else {
                            vm.showToast(context.getString(R.string.webdav_saved))
                        }
                    }
                ) { Text(stringResource(R.string.webdav_http_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showHttpWarning = false
                        pendingAfterHttpAck = null
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ------------------------------------------------- server certificate

    val promptedSha256 = certPrompt
    if (promptedSha256 != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.webdav_cert_trust_title)) },
            text = {
                DialogBodyText(
                    stringResource(
                        R.string.webdav_cert_trust_body,
                        WebDavClient.formatFingerprint(promptedSha256)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        certPin = promptedSha256
                        certPrompt = null
                        vm.saveWebDavConfig(currentConfig())
                        vm.showToast(context.getString(R.string.webdav_cert_trusted))
                    }
                ) { Text(stringResource(R.string.webdav_cert_trust_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { certPrompt = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ---------------------------------------------------- backup password

    if (showBackupDialog) {
        BackupPasswordDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { exportPassword, remember ->
                showBackupDialog = false
                // Store first: the upload below can fail on the network, but the
                // user's "remember this" choice is about the password, not the run.
                if (remember) vm.setWebDavBackupPassword(exportPassword)
                startBackup(exportPassword)
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
            onDismissRequest = {},
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
            onDismissRequest = {},
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
            onDismissRequest = {},
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
internal fun BackupPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (password: String, remember: Boolean) -> Unit,
    titleRes: Int = R.string.webdav_backup_password_title,
    descriptionRes: Int = R.string.webdav_backup_password_desc,
    /**
     * Offer "remember this password". The same dialog also serves the password
     * management page, where the password is stored unconditionally and a
     * checkbox would be noise.
     */
    showRemember: Boolean = true
) {
    val context = LocalContext.current
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    // Remembering is the default: the dialog exists on every backup only
    // because nothing was remembered yet, and the user can still untick it.
    // (Named rememberPassword, not remember: a local val named `remember`
    // would shadow the Compose function and break the line below.)
    var rememberPassword by rememberSaveable { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(descriptionRes),
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
                if (showRemember) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = rememberPassword,
                            onCheckedChange = { rememberPassword = it }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = stringResource(R.string.webdav_backup_password_remember),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.webdav_backup_password_remember_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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
                    if (error == null) onConfirm(password, showRemember && rememberPassword)
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
        onDismissRequest = {},
        title = { Text(stringResource(R.string.import_password_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
