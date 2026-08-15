package com.safekey.authenticator.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import android.graphics.BitmapFactory
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.totp.OtpUriParser
import com.safekey.authenticator.totp.ParsedOtpUri
import com.safekey.authenticator.ui.components.QrCameraPreview
import com.safekey.authenticator.ui.components.SimpleTopBar
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    vm: MainViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var confirm by remember { mutableStateOf<ParsedOtpUri?>(null) }
    var showPermissionError by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (!granted) showPermissionError = true
    }

    // Gallery pick: system SAF picker, no storage permission needed.
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
                            if (raw != null) {
                                val parsed = try {
                                    OtpUriParser.parse(raw)
                                } catch (_: Exception) {
                                    null
                                }
                                if (parsed != null) {
                                    confirm = parsed
                                } else {
                                    vm.showToast(context.getString(R.string.scan_no_uri))
                                }
                            } else {
                                vm.showToast(context.getString(R.string.scan_no_qr))
                            }
                        }
                        .addOnFailureListener {
                            vm.showToast(context.getString(R.string.scan_gallery_failed))
                        }
                } else {
                    vm.showToast(context.getString(R.string.scan_gallery_failed))
                }
            } catch (_: Exception) {
                vm.showToast(context.getString(R.string.scan_gallery_failed))
            }
        }
    }

    Scaffold(
        topBar = { SimpleTopBar(title = stringResource(R.string.scan_qr), onBack = onBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (hasPermission) {
                Column(modifier = Modifier.fillMaxSize()) {
                    QrCameraPreview(
                        enabled = confirm == null,
                        onRawCode = { raw ->
                            val parsed = try {
                                OtpUriParser.parse(raw)
                            } catch (_: Exception) {
                                null
                            }
                            if (parsed != null) {
                                confirm = parsed
                            } else {
                                vm.showToast(context.getString(R.string.scan_no_uri))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.scan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 16.dp)
                    )
                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(stringResource(R.string.scan_from_gallery))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.camera_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.camera_permission_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(stringResource(R.string.grant_permission))
                    }
                }
            }
        }
    }

    confirm?.let { parsed ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.add_account)) },
            text = {
                Column {
                    Text(
                        parsed.issuer.ifBlank { parsed.label },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (parsed.issuer.isNotBlank()) {
                        Text(
                            parsed.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.totp_params, parsed.algorithm, parsed.digits, parsed.period),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addAccount(
                        parsed.issuer, parsed.label, parsed.secret,
                        parsed.algorithm, parsed.digits, parsed.period,
                        parsed.type, parsed.counter
                    )
                    confirm = null
                    onSaved()
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showPermissionError) {
        AlertDialog(
            onDismissRequest = { showPermissionError = false },
            title = { Text(stringResource(R.string.camera_permission_title)) },
            text = { Text(stringResource(R.string.camera_permission_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionError = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text(stringResource(R.string.grant_permission)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionError = false }) { Text(stringResource(R.string.close)) }
            }
        )
    }
}
