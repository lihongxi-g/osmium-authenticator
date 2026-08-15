package com.safekey.authenticator.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.totp.GoogleMigrationParser
import com.safekey.authenticator.ui.components.QrCameraPreview
import com.safekey.authenticator.ui.components.SimpleTopBar

/**
 * Import accounts from a Google Authenticator "Transfer accounts" QR code
 * (otpauth-migration://offline?data=...).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleImportScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val accounts by vm.accounts.collectAsState()

    var migrationList by remember { mutableStateOf<List<GoogleMigrationParser.MigrationAccount>?>(null) }
    var selected by remember { mutableStateOf<Set<Int>?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var pasteInput by remember { mutableStateOf("") }

    fun onRawMigrationCode(raw: String) {
        if (!GoogleMigrationParser.isMigrationUri(raw)) {
            errorText = context.getString(R.string.migration_not_google_code)
            return
        }
        try {
            val parsed = GoogleMigrationParser.parse(raw)
            migrationList = parsed
            selected = parsed.indices.toSet()
            errorText = null
        } catch (e: IllegalArgumentException) {
            errorText = e.message ?: context.getString(R.string.migration_parse_failed)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
                if (bitmap != null) {
                    val image = InputImage.fromBitmap(bitmap, 0)
                    BarcodeScanning.getClient()
                        .process(image)
                        .addOnSuccessListener { barcodes ->
                            val raw = barcodes.firstOrNull()?.rawValue
                            if (raw == null) {
                                errorText = context.getString(R.string.scan_no_qr)
                            } else {
                                onRawMigrationCode(raw)
                            }
                        }
                        .addOnFailureListener {
                            errorText = context.getString(R.string.scan_gallery_failed)
                        }
                } else {
                    errorText = context.getString(R.string.scan_gallery_failed)
                }
            } catch (_: Exception) {
                errorText = context.getString(R.string.scan_gallery_failed)
            }
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.migration_title), onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            val list = migrationList
            if (list == null) {
                // ---- scan / paste stage ----
                Text(
                    text = stringResource(R.string.migration_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                QrCameraPreview(
                    enabled = true,
                    onRawCode = { raw -> onRawMigrationCode(raw) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
                if (errorText != null) {
                    Text(
                        text = errorText ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pasteInput,
                        onValueChange = { pasteInput = it },
                        label = { Text(stringResource(R.string.migration_paste_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onRawMigrationCode(pasteInput)
                    }) {
                        Text(stringResource(R.string.migration_parse_button))
                    }
                }
                TextButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.scan_from_gallery))
                }
            } else {
                // ---- preview / import stage ----
                Text(
                    text = stringResource(R.string.migration_found, list.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                list.forEachIndexed { index, account ->
                    val isSelected = selected?.contains(index) == true
                    val existingSecret = accounts.any { it.secret == account.secret }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                val s = selected?.toMutableSet() ?: mutableSetOf()
                                if (checked) s.add(index) else s.remove(index)
                                selected = s
                            },
                            enabled = !account.isUnsupported && !existingSecret
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.issuer.ifBlank { account.name },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val typeLabel = if (account.type == "hotp")
                                stringResource(R.string.migration_hotp_badge)
                            else stringResource(R.string.migration_totp_badge)
                            val statusLabel = when {
                                account.isUnsupported -> stringResource(R.string.migration_md5_unsupported)
                                existingSecret -> stringResource(R.string.migration_duplicate)
                                else -> "${account.name} · $typeLabel"
                            }
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (account.isUnsupported || existingSecret)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        val s = selected.orEmpty()
                        var imported = 0
                        list.forEachIndexed { index, account ->
                            if (index in s && !account.isUnsupported &&
                                accounts.none { it.secret == account.secret }
                            ) {
                                vm.addAccount(
                                    issuer = account.issuer,
                                    label = account.name,
                                    secret = account.secret,
                                    algorithm = account.algorithm,
                                    digits = account.digits,
                                    period = 30,
                                    type = account.type,
                                    counter = account.counter
                                )
                                imported++
                            }
                        }
                        vm.showToast(context.getString(R.string.migration_imported, imported))
                        onImported()
                    },
                    enabled = !selected.isNullOrEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(stringResource(R.string.migration_import_button))
                }
                TextButton(
                    onClick = {
                        migrationList = null
                        selected = null
                        pasteInput = ""
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(stringResource(R.string.migration_scan_another))
                }
            }
        }
    }
}
