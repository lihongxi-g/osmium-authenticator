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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.model.VaultFile
import com.safekey.authenticator.repository.ImportMerger
import com.safekey.authenticator.repository.ImportPlan
import com.safekey.authenticator.security.VaultCrypto
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ---------------------------------------------------------------- Export

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    vm: MainViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var pendingJson by remember { mutableStateOf<String?>(null) }

    val createFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val payload = pendingJson
                if (payload != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                out.write(payload.toByteArray(Charsets.UTF_8))
                            } ?: throw IllegalStateException("Cannot open output stream")
                        }
                        vm.showToast(context.getString(R.string.export_done))
                        pendingJson = null
                        onDone()
                    } catch (e: Exception) {
                        error = context.getString(R.string.export_failed, e.message ?: "IOException")
                    }
                }
                exporting = false
            }
        } else {
            exporting = false
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.export_vault), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.export_password_desc),
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
            if (error != null) {
                Text(
                    text = error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    error = null
                    if (password.isEmpty()) {
                        error = context.getString(R.string.error_password_empty)
                        return@Button
                    }
                    if (password.length < 8) {
                        error = context.getString(R.string.error_password_weak)
                        return@Button
                    }
                    if (password != confirm) {
                        error = context.getString(R.string.error_password_mismatch)
                        return@Button
                    }
                    exporting = true
                    scope.launch {
                        val vaultJson = try {
                            withContext(Dispatchers.IO) {
                                val repo = (context.applicationContext as com.safekey.authenticator.SafeKeyApp).accountRepository
                                val pinHash = vm.pinManager.getPinHashForExport()
                                val vf = repo.exportVault(
                                    pinSalt = pinHash?.first ?: "",
                                    pinHash = pinHash?.second ?: ""
                                )
                                val json = Json { encodeDefaults = true }
                                val plain = json.encodeToString(vf)
                                VaultCrypto.encrypt(plain, password.toCharArray())
                            }
                        } catch (e: Exception) {
                            error = context.getString(R.string.export_failed, e.message ?: "Error")
                            exporting = false
                            null
                        }
                        if (vaultJson != null) {
                            pendingJson = vaultJson
                            createFileLauncher.launch("safekey-backup.json")
                        } else {
                            exporting = false
                        }
                    }
                },
                enabled = !exporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_vault))
            }
        }
    }
}

// ---------------------------------------------------------------- Import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    vm: MainViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var selected by remember { mutableStateOf<Set<Int>?>(null) } // null = all selected
    var working by remember { mutableStateOf(false) }
    var pinPending by remember { mutableStateOf<VaultFile?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            working = true
            scope.launch {
                try {
                    val payload = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Cannot read file")
                    }
                    // Stage 1: decrypt — failures here mean wrong password / corrupt file
                    val vaultJson = try {
                        withContext(Dispatchers.Default) {
                            VaultCrypto.decrypt(String(payload, Charsets.UTF_8), password.toCharArray())
                        }
                    } catch (e: Exception) {
                        error = context.getString(R.string.error_import_wrong_password)
                        working = false
                        return@launch
                    }
                    // Stage 2: parse — failures here mean not a SafeKey vault
                    val vault: VaultFile = try {
                        Json { ignoreUnknownKeys = true }.decodeFromString(vaultJson)
                    } catch (e: Exception) {
                        error = context.getString(R.string.error_import_format)
                        working = false
                        return@launch
                    }
                    if (vault.format != "safekey-vault") {
                        error = context.getString(R.string.error_import_format)
                        working = false
                        return@launch
                    }
                    val existing = vm.accounts.value
                    // PIN gate: the file itself carries a PIN, or this device has one
                    if (vault.pinSalt.isNotEmpty() || vm.hasLocalPin()) {
                        pinPending = vault
                        pinError = null
                        plan = null
                    } else {
                        val p = ImportMerger.plan(existing, vault.accounts)
                        plan = p
                        selected = null
                        error = null
                    }
                } catch (e: Exception) {
                    error = context.getString(R.string.error_import_format)
                    plan = null
                }
                working = false
            }
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.import_vault), onBack = onBack) }
    ) { padding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val pendingPin = pinPending
            if (pendingPin != null) {
                PinVerifyScreen(
                    title = stringResource(R.string.import_pin_title),
                    subtitle = stringResource(R.string.import_pin_desc),
                    error = pinError,
                    remainingAttempts = null,
                    onVerify = { pin ->
                        val ok = if (pendingPin.pinSalt.isNotEmpty()) {
                            vm.verifyImportPin(pin, pendingPin.pinSalt, pendingPin.pinHash)
                        } else {
                            vm.verifyLocalPin(pin)
                        }
                        if (ok) {
                            val p = ImportMerger.plan(vm.accounts.value, pendingPin.accounts)
                            plan = p
                            selected = null
                            pinPending = null
                            pinError = null
                        } else {
                            // self-destruct PIN works at every PIN prompt
                            vm.checkSelfDestructPin(pin)
                            pinError = context.getString(R.string.pin_wrong)
                        }
                    },
                    onCancel = { pinPending = null }
                )
            } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val currentPlan = plan
            if (currentPlan == null) {
                Text(
                    text = stringResource(R.string.import_password_desc),
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
                if (error != null) {
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        if (password.isEmpty()) {
                            error = context.getString(R.string.error_password_empty)
                        } else {
                            openFileLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.import_vault))
                }
            } else {
                // Preview & confirm
                Text(
                    text = stringResource(R.string.import_preview_title, currentPlan.total),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.import_preview_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (currentPlan.duplicatesCount > 0) {
                    Text(
                        text = stringResource(R.string.import_duplicate_note, currentPlan.duplicatesCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                val all = (currentPlan.toAdd + currentPlan.toUpdate.map { it.second }).toList()
                val sel = selected
                if (sel != null) {
                    TextButton(
                        onClick = {
                            selected = if (sel.size == all.size) emptySet()
                            else all.indices.toSet()
                        }
                    ) { Text(stringResource(R.string.select_all)) }
                }
                all.forEachIndexed { index, va ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = sel?.contains(index) ?: true,
                            onCheckedChange = { checked ->
                                val current = selected ?: all.indices.toSet()
                                selected = if (checked) current + index else current - index
                            }
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = va.issuer.ifBlank { va.label },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = va.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        working = true
                        val chosen = selected ?: all.indices.toSet()
                        val add = currentPlan.toAdd.filterIndexed { i, _ -> i in chosen }
                        val upd = currentPlan.toUpdate.filterIndexed { i, _ ->
                            (currentPlan.toAdd.size + i) in chosen
                        }
                        vm.applyImport(add, upd) { count ->
                            vm.showToast(context.getString(R.string.import_done, count))
                            onDone()
                        }
                    },
                    enabled = !working && (selected?.isNotEmpty() ?: true),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.confirm))
                }
                TextButton(onClick = { plan = null; error = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
            }
        }
    }
}
