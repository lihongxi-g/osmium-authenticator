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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.totp.exporter.ExportFormat
import com.safekey.authenticator.totp.exporter.Exporters
import com.safekey.authenticator.ui.components.DialogBodyText
import com.safekey.authenticator.ui.components.SimpleTopBar
import com.safekey.authenticator.ui.components.sendFeedbackWithLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import com.safekey.authenticator.totp.GoogleMigrationEncoder
import com.safekey.authenticator.ui.components.AppIcons
import com.safekey.authenticator.ui.components.renderQrBitmap

/**
 * "Export to another authenticator" — the deliberate exit door.
 *
 * Deliberately NOT behind developer mode and NOT encrypted: a migration target
 * needs plaintext it can read. The three friction points that ARE here:
 *  1. the identity check on the settings row that leads here,
 *  2. an explicit plaintext warning with the file's consequences spelled out,
 *  3. the two farewell dialogs — a last honest pitch before the file is
 *     written, and a thank-you afterwards that also offers a feedback channel.
 *
 * Nothing is written until the user picks a destination in the system save
 * dialog, and the dialog's suggested file name carries the format extension.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportToOthersScreen(
    vm: MainViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /** The farewell pitch is shown once, on arrival. */
    var showFarewell by remember { mutableStateOf(true) }
    /** Set when the user chose "I am leaving" — unlocks the export controls. */
    var leaving by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(ExportFormat.OTP_LINKS) }
    var pendingText by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    /** Shown after the file was written successfully. */
    var showGoodbye by remember { mutableStateOf(false) }
    /** Non-null while the Google Authenticator QR dialog is open. */
    var googleTransfer by remember { mutableStateOf<GoogleMigrationEncoder.Result?>(null) }
    var preparingGoogle by remember { mutableStateOf(false) }

    fun write(uri: Uri?) {
        vm.setTransferPickerActive(false)
        val text = pendingText
        if (uri == null || text == null) {
            working = false
            return
        }
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Cannot open output stream")
                }
                vm.showToast(context.getString(R.string.export_other_done))
                showGoodbye = true
            } catch (e: Exception) {
                error = context.getString(R.string.export_other_fail, e.message ?: "IOException")
            } finally {
                working = false
            }
        }
    }

    val onPicked: (Uri?) -> Unit = { uri -> write(uri) }
    val textLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.OTP_LINKS.mimeType), onPicked
    )
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.AEGIS_JSON.mimeType), onPicked
    )
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.KEEPASS_CSV.mimeType), onPicked
    )

    /**
     * Reads the vault the same way the file export does — the QR carries raw
     * secrets, so it goes through the identical plaintext path rather than a
     * second, laxer one.
     */
    fun startGoogleTransfer() {
        error = null
        preparingGoogle = true
        scope.launch {
            try {
                val accounts = withContext(Dispatchers.IO) {
                    val app = context.applicationContext as SafeKeyApp
                    val pin = vm.pinManager.getPinHashForExport()
                    val export = app.accountRepository.exportVault(pin?.first ?: "", pin?.second ?: "")
                    if (export.dropped > 0) {
                        throw IllegalStateException("${export.dropped} accounts could not be decrypted")
                    }
                    export.vault.accounts
                }
                val result = withContext(Dispatchers.Default) {
                    GoogleMigrationEncoder.build(
                        accounts = accounts,
                        batchId = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
                    )
                }
                if (result.isEmpty) {
                    error = context.getString(R.string.ga_transfer_nothing)
                } else {
                    googleTransfer = result
                }
            } catch (e: Exception) {
                error = context.getString(R.string.export_other_fail, e.message ?: "Error")
            } finally {
                preparingGoogle = false
            }
        }
    }

    fun startExport() {
        error = null
        working = true
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val app = context.applicationContext as SafeKeyApp
                    val pin = vm.pinManager.getPinHashForExport()
                    val export = app.accountRepository.exportVault(pin?.first ?: "", pin?.second ?: "")
                    if (export.dropped > 0) {
                        throw IllegalStateException("${export.dropped} accounts could not be decrypted")
                    }
                    Exporters.build(format, export.vault.accounts)
                }
                if (text.isBlank()) {
                    error = context.getString(R.string.export_other_empty)
                    working = false
                    return@launch
                }
                pendingText = text
                vm.setTransferPickerActive(true)
                try {
                    when (format) {
                        ExportFormat.OTP_LINKS -> textLauncher.launch(format.fileName)
                        ExportFormat.AEGIS_JSON -> jsonLauncher.launch(format.fileName)
                        ExportFormat.KEEPASS_CSV -> csvLauncher.launch(format.fileName)
                    }
                } catch (e: Exception) {
                    vm.setTransferPickerActive(false)
                    error = context.getString(R.string.export_other_fail, e.message ?: "Error")
                    working = false
                }
            } catch (e: Exception) {
                error = context.getString(R.string.export_other_fail, e.message ?: "Error")
                working = false
            }
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.export_other_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.export_other_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.export_other_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            ExportFormat.values().forEach { option ->
                FormatCard(
                    format = option,
                    selected = format == option,
                    onSelect = { format = option }
                )
            }

            GoogleTransferCard(
                enabled = !preparingGoogle && leaving,
                onClick = { startGoogleTransfer() }
            )

            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { startExport() },
                enabled = !working && leaving,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.export_other_button)) }
            Spacer(Modifier.height(8.dp))
        }
    }

    googleTransfer?.let { result ->
        GoogleTransferDialog(result = result, onClose = { googleTransfer = null })
    }

    if (showFarewell) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "\uD83D\uDE2D", fontSize = 64.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.farewell_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.farewell_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showFarewell = false
                    leaving = true
                }) { Text(stringResource(R.string.farewell_leave)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFarewell = false
                    onBack()
                }) { Text(stringResource(R.string.farewell_keep)) }
            }
        )
    }

    if (showGoodbye) {
        AlertDialog(
            onDismissRequest = {},
            title = null,
            text = {
                DialogBodyText(
                    stringResource(R.string.farewell_bye_body),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGoodbye = false
                    onDone()
                }) { Text(stringResource(R.string.farewell_bye_ok)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showGoodbye = false
                    sendFeedbackWithLog(context, vm)
                    onDone()
                }) { Text(stringResource(R.string.farewell_bye_words)) }
            }
        )
    }
}

/** One selectable export format. */
@Composable
private fun FormatCard(
    format: ExportFormat,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val title = when (format) {
        ExportFormat.OTP_LINKS -> stringResource(R.string.export_other_fmt_links)
        ExportFormat.AEGIS_JSON -> stringResource(R.string.export_other_fmt_aegis)
        ExportFormat.KEEPASS_CSV -> stringResource(R.string.export_other_fmt_csv)
    }
    val description = when (format) {
        ExportFormat.OTP_LINKS -> stringResource(R.string.export_other_fmt_links_desc)
        ExportFormat.AEGIS_JSON -> stringResource(R.string.export_other_fmt_aegis_desc)
        ExportFormat.KEEPASS_CSV -> stringResource(R.string.export_other_fmt_csv_desc)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onSelect)
                .padding(12.dp)
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Entry point for the Google Authenticator transfer: unlike the three file
 * formats, this one shows QR codes and writes nothing to disk.
 */
@Composable
private fun GoogleTransferCard(enabled: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = false, enabled = enabled, onClick = onClick)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = AppIcons.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.export_other_fmt_google),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.export_other_fmt_google_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Shows the migration QR codes, one batch per page, and spells out which
 * accounts the format cannot carry — a silent drop would look like a successful
 * transfer until the missing codes are needed.
 */
@Composable
private fun GoogleTransferDialog(
    result: GoogleMigrationEncoder.Result,
    onClose: () -> Unit
) {
    var index by remember(result) { mutableStateOf(0) }
    val uri = result.uris.getOrNull(index) ?: return

    Dialog(onDismissRequest = onClose) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.ga_transfer_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))

                val bitmap = remember(uri) { renderQrBitmap(uri, 720) }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.ga_transfer_qr_desc),
                    modifier = Modifier
                        .size(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp)
                )

                if (result.uris.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.ga_transfer_page,
                            index + 1,
                            result.uris.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { if (index > 0) index-- },
                            enabled = index > 0
                        ) { Text("‹") }
                        Text(
                            text = "${result.exported.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { if (index < result.uris.lastIndex) index++ },
                            enabled = index < result.uris.lastIndex
                        ) { Text("›") }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ga_transfer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (result.skipped.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.ga_transfer_skipped_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    result.skipped.forEach { skipped ->
                        Text(
                            text = stringResource(
                                R.string.ga_transfer_skipped_line,
                                skipped.account.issuer.ifBlank { skipped.account.label },
                                stringResource(reasonRes(skipped.reason))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.ga_transfer_close)) }
            }
        }
    }
}

private fun reasonRes(reason: GoogleMigrationEncoder.SkipReason): Int = when (reason) {
    GoogleMigrationEncoder.SkipReason.PERIOD -> R.string.ga_transfer_reason_period
    GoogleMigrationEncoder.SkipReason.STEAM -> R.string.ga_transfer_reason_steam
    GoogleMigrationEncoder.SkipReason.OTHER -> R.string.ga_transfer_reason_other
}
