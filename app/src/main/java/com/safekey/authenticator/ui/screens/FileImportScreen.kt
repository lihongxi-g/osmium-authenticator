package com.safekey.authenticator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import com.safekey.authenticator.repository.ImportPlan
import com.safekey.authenticator.totp.importer.EntryIssue
import com.safekey.authenticator.totp.importer.ImporterError
import com.safekey.authenticator.totp.importer.ImporterException
import com.safekey.authenticator.totp.importer.Importers
import com.safekey.authenticator.totp.importer.ImportSupport
import com.safekey.authenticator.ui.components.SimpleTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import accounts from another authenticator's export FILE (Aegis / 2FAS /
 * Raivo OTP).
 *
 * Flow: SAF file picker → read text → auto-detect the source format
 * (Importers.find — the user never picks a source app) → parse → merge with
 * the existing accounts (ImportMerger via vm.prepareImport) → preview with
 * per-row checkboxes → save through the same repository path as vault
 * imports.
 *
 * Rows Osmium cannot reproduce (unknown type/algorithm, 7-digit TOTP,
 * invalid secret) stay visible with a disabled checkbox and a reason — they
 * are never silently dropped or imported with mangled parameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileImportScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /** Parsed accounts (all of them, importable or not) + per-entry issues. */
    var accounts by remember { mutableStateOf<List<VaultAccount>?>(null) }
    var issues by remember { mutableStateOf<Map<Int, EntryIssue>>(emptyMap()) }
    /** Merge plan over the importable subset, from a fresh DB snapshot. */
    var plan by remember { mutableStateOf<ImportPlan?>(null) }
    var selected by remember { mutableStateOf<Set<Int>?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        vm.setTransferPickerActive(false)
        if (uri != null) scope.launch {
            working = true
            errorText = null
            accounts = null
            issues = emptyMap()
            plan = null
            selected = null
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw ImporterException(ImporterError.UNRECOGNIZED)
                }
                if (text.isBlank()) {
                    errorText = context.getString(R.string.fileimport_empty_file)
                    return@launch
                }
                val parsed = withContext(Dispatchers.Default) {
                    val importer = Importers.find(text)
                        ?: throw ImporterException(ImporterError.UNRECOGNIZED)
                    importer.parse(text)
                }
                val issueMap = parsed.mapIndexedNotNull { index, account ->
                    ImportSupport.issue(account)?.let { index to it }
                }.toMap()
                val importable = parsed.filterIndexed { index, _ -> index !in issueMap }
                accounts = parsed
                issues = issueMap
                if (importable.isNotEmpty()) {
                    // Merge with a FRESH snapshot so duplicates are detected
                    // correctly (never against a stale flow value).
                    vm.prepareImport(emptyList(), importable) { importPlan ->
                        plan = importPlan
                        selected = null // null = all importable rows selected
                    }
                } else {
                    plan = ImportPlan(emptyList(), emptyList(), 0)
                }
            } catch (e: ImporterException) {
                errorText = when (e.error) {
                    ImporterError.ENCRYPTED_UNSUPPORTED ->
                        context.getString(R.string.fileimport_error_encrypted)
                    ImporterError.NOT_JSON ->
                        context.getString(R.string.fileimport_error_not_json)
                    ImporterError.VERSION_UNSUPPORTED ->
                        context.getString(R.string.fileimport_error_version)
                    ImporterError.EMPTY ->
                        context.getString(R.string.fileimport_error_empty)
                    ImporterError.UNRECOGNIZED ->
                        context.getString(R.string.fileimport_error_unknown)
                }
            } catch (e: Exception) {
                errorText = context.getString(R.string.fileimport_error_read, e.message ?: "Error")
            } finally {
                working = false
            }
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.fileimport_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val parsed = accounts
            if (parsed == null) {
                // ---------- file picker stage ----------
                Text(
                    text = stringResource(R.string.fileimport_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                Button(
                    onClick = {
                        errorText = null
                        vm.setTransferPickerActive(true)
                        try { fileLauncher.launch(arrayOf("*/*")) } catch (e: Exception) {
                            vm.setTransferPickerActive(false)
                            errorText = context.getString(R.string.fileimport_error_read, e.message ?: "Error")
                        }
                    },
                    enabled = !working,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text(stringResource(R.string.fileimport_pick_button)) }
                Text(
                    text = stringResource(R.string.fileimport_security_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            } else {
                // ---------- preview / import stage ----------
                val current = plan
                val issueRows = parsed.mapIndexedNotNull { index, account ->
                    issues[index]?.let { issue -> account to issue }
                }
                if (current == null) {
                    Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                } else {
                    val toAdd = current.toAdd
                    val toUpdate = current.toUpdate
                    val importableRows = toAdd + toUpdate.map { it.second }
                    if (importableRows.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.fileimport_found, importableRows.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                        if (current.duplicatesCount > 0) {
                            Text(
                                text = stringResource(R.string.import_duplicate_note, current.duplicatesCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                    importableRows.forEachIndexed { index, account ->
                        val isSelected = selected?.contains(index) ?: true
                        val isUpdate = index >= toAdd.size
                        ImportRow(
                            title = account.issuer.ifBlank { account.label },
                            subtitle = fileImportSubtitle(context, account),
                            note = if (isUpdate) context.getString(R.string.fileimport_will_update) else null,
                            noteIsError = false,
                            checked = isSelected,
                            enabled = true,
                            onCheckedChange = { checked ->
                                val base = selected ?: importableRows.indices.toSet()
                                selected = if (checked) base + index else base - index
                            }
                        )
                    }
                    issueRows.forEach { (account, issue) ->
                        ImportRow(
                            title = account.issuer.ifBlank { account.label },
                            subtitle = account.label.ifBlank { "Unknown" },
                            note = fileImportIssueText(context, issue),
                            noteIsError = true,
                            checked = false,
                            enabled = false,
                            onCheckedChange = null
                        )
                    }
                    val chosen = selected ?: importableRows.indices.toSet()
                    if (importableRows.isNotEmpty()) {
                        Button(
                            onClick = {
                                working = true
                                val add = toAdd.filterIndexed { index, _ -> index in chosen }
                                val update = toUpdate.filterIndexed { index, _ -> toAdd.size + index in chosen }
                                    .map { (existing, incoming) -> existing to incoming }
                                vm.applyImport(add, update) { count ->
                                    vm.showToast(context.getString(R.string.import_done, count))
                                    onImported()
                                }
                            },
                            enabled = !working && chosen.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) { Text(stringResource(R.string.fileimport_import_button)) }
                    }
                    TextButton(
                        onClick = {
                            accounts = null
                            issues = emptyMap()
                            plan = null
                            selected = null
                            errorText = null
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text(stringResource(R.string.fileimport_pick_another)) }
                }
            }
        }
    }
}

@Composable
private fun ImportRow(
    title: String,
    subtitle: String,
    note: String?,
    noteIsError: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onCheckedChange != null) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        } else {
            // Unsupported row: disabled, unchecked box (same visual language
            // as the Google migration preview's MD5/duplicate rows).
            Checkbox(
                checked = false,
                onCheckedChange = {},
                enabled = false
            )
        }
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (noteIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "label · TOTP" style line, mirroring the Google migration preview. */
private fun fileImportSubtitle(context: android.content.Context, account: VaultAccount): String {
    val typeLabel = when {
        account.issuer.equals("Steam", ignoreCase = true) ->
            context.getString(R.string.fileimport_steam_badge)
        account.type == Account.TYPE_HOTP ->
            context.getString(R.string.migration_hotp_badge)
        else -> context.getString(R.string.migration_totp_badge)
    }
    return context.getString(R.string.fileimport_subtitle, account.label.ifBlank { "Unknown" }, typeLabel)
}

private fun fileImportIssueText(context: android.content.Context, issue: EntryIssue): String =
    when (issue) {
        EntryIssue.UNSUPPORTED_TYPE -> context.getString(R.string.fileimport_reason_type)
        EntryIssue.UNSUPPORTED_ALGORITHM -> context.getString(R.string.fileimport_reason_algorithm)
        EntryIssue.UNSUPPORTED_DIGITS -> context.getString(R.string.fileimport_reason_digits)
        EntryIssue.INVALID_SECRET -> context.getString(R.string.fileimport_reason_secret)
    }
