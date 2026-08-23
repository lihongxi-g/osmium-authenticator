package com.safekey.authenticator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.repository.ImportPlan
import com.safekey.authenticator.security.VaultFormatException
import com.safekey.authenticator.security.VaultIO
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(vm: MainViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        vm.setTransferPickerActive(false)
        scope.launch {
            try {
                val payload = pendingJson ?: error("No export payload")
                if (uri != null) withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(payload.toByteArray()) } ?: error("Cannot open output") }
                if (uri != null) { vm.showToast(context.getString(R.string.export_done)); onDone() }
            } catch (e: Exception) { error = context.getString(R.string.export_failed, e.message ?: "IOException") }
            exporting = false
        }
    }
    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.export_vault), onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.export_password_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.export_password_hint)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(confirm, { confirm = it }, label = { Text(stringResource(R.string.export_confirm_hint)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !exporting, onClick = {
                error = when { password.isEmpty() -> context.getString(R.string.error_password_empty); password.length < 8 -> context.getString(R.string.error_password_weak); password != confirm -> context.getString(R.string.error_password_mismatch); else -> null }
                if (error == null) scope.launch {
                    exporting = true
                    try {
                        val json = withContext(Dispatchers.IO) {
                            val app = context.applicationContext as com.safekey.authenticator.SafeKeyApp
                            val pin = vm.pinManager.getPinHashForExport()
                            VaultIO.encrypt(app.accountRepository.exportVault(pin?.first ?: "", pin?.second ?: ""), password.toCharArray())
                        }
                        pendingJson = json
                        vm.setTransferPickerActive(true)
                        try { launcher.launch("osmium-backup.json") }
                        catch (e: Exception) { vm.setTransferPickerActive(false); error = context.getString(R.string.export_failed, e.message ?: "Error") }
                    } catch (e: Exception) { error = context.getString(R.string.export_failed, e.message ?: "Error"); exporting = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_vault)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: MainViewModel, onDone: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var vault by remember { mutableStateOf<VaultFile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        vm.setTransferPickerActive(false)
        if (uri != null) scope.launch {
            working = true
            try {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read") }
                vault = withContext(Dispatchers.Default) { VaultIO.decrypt(bytes, password.toCharArray()) }
                error = null
            } catch (e: VaultFormatException) { error = context.getString(if (e.wrongPassword) R.string.error_import_wrong_password else R.string.error_import_format) }
            catch (_: Exception) { error = context.getString(R.string.error_import_format) }
            working = false
        }
    }
    Scaffold(topBar = { SimpleTopBar(stringResource(R.string.import_vault), onBack) }) { padding ->
        val parsed = vault
        if (parsed == null) Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.import_password_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.export_password_hint)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !working && password.isNotEmpty(), onClick = {
                vm.setTransferPickerActive(true)
                try { launcher.launch(arrayOf("*/*")) }
                catch (e: Exception) { vm.setTransferPickerActive(false); vm.showToast(e.message ?: "Error") }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_vault)) }
        } else VaultImportFlow(vm, parsed, onDone) { vault = null; error = null }
    }
}

@Composable
fun VaultImportFlow(vm: MainViewModel, vault: VaultFile, onDone: () -> Unit, onBackToPassword: () -> Unit) {
    val context = LocalContext.current
    var plan by remember(vault) { mutableStateOf<ImportPlan?>(null) }
    var selected by remember(vault) { mutableStateOf<Set<Int>?>(null) }
    var working by remember { mutableStateOf(false) }
    var pinPending by remember(vault) { mutableStateOf(if (vault.pinSalt.isNotEmpty() || vm.hasLocalPin()) vault else null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun prepare() {
        vm.prepareImport(vault.tags, vault.accounts) { plan = it }
        selected = null
    }
    LaunchedEffect(vault) { if (pinPending == null) prepare() }

    pinPending?.let { pending ->
        PinVerifyScreen(
            title = stringResource(R.string.import_pin_title), subtitle = stringResource(R.string.import_pin_desc), error = pinError, remainingAttempts = null,
            onVerify = { pin ->
                // A foreign/corrupt file could carry malformed pin data —
                // verification must degrade to "wrong PIN", never crash.
                val ok = try {
                    if (pending.pinSalt.isNotEmpty()) vm.verifyImportPin(pin, pending.pinSalt, pending.pinHash) else vm.verifyLocalPin(pin)
                } catch (_: Exception) {
                    false
                }
                if (ok) { pinPending = null; prepare() } else { vm.checkSelfDestructPin(pin); pinError = context.getString(R.string.pin_wrong) }
            },
            onCancel = onBackToPassword
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val current = plan ?: run { Spacer(Modifier.height(4.dp)); return@Column }
        Text(stringResource(R.string.import_preview_title, current.total), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.import_preview_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        val all = current.toAdd + current.toUpdate.map { it.second }
        all.forEachIndexed { index, account ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected?.contains(index) ?: true, onCheckedChange = { checked -> val set = selected ?: all.indices.toSet(); selected = if (checked) set + index else set - index })
                Column(Modifier.padding(start = 4.dp)) { Text(account.issuer.ifBlank { account.label }); Text(account.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Button(enabled = !working && (selected?.isNotEmpty() ?: true), onClick = {
            working = true
            val chosen = selected ?: all.indices.toSet()
            val add = current.toAdd.filterIndexed { i, _ -> i in chosen }.map { it.copy(tagIds = vm.remapImportedTagIds(it.tagIds).toList()) }
            val update = current.toUpdate.filterIndexed { i, _ -> current.toAdd.size + i in chosen }.map { (account, incoming) -> account to incoming.copy(tagIds = vm.remapImportedTagIds(incoming.tagIds).toList()) }
            vm.applyImport(add, update) { count -> vm.showToast(context.getString(R.string.import_done, count)); onDone() }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.confirm)) }
        TextButton(onClick = onBackToPassword) { Text(stringResource(R.string.cancel)) }
    }
}
