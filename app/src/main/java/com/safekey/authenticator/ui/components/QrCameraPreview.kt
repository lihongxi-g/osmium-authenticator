package com.safekey.authenticator.ui.components

import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX + ML Kit QR scanner shared by the otpauth:// scan screen and the
 * Google Authenticator migration import. Emits raw QR payload strings — the
 * caller decides how to interpret them.
 *
 * NOTE: the controller MUST be bound to the lifecycle (bindToLifecycle),
 * otherwise it never attaches and the preview stays black.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
fun QrCameraPreview(
    enabled: Boolean,
    onRawCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            bindToLifecycle(lifecycleOwner)
        }
    }
    val scanning = remember { AtomicBoolean(true) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val enabledState by rememberUpdatedState(enabled)

    val analyzer = remember {
        ImageAnalysis.Analyzer { imageProxy ->
            if (!enabledState || !scanning.get()) {
                imageProxy.close()
                return@Analyzer
            }
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                BarcodeScanning.getClient()
                    .process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstOrNull()?.rawValue
                        if (raw != null && scanning.get()) {
                            scanning.set(false)
                            mainHandler.post { onRawCode(raw) }
                        }
                        imageProxy.close()
                    }
                    .addOnFailureListener { imageProxy.close() }
            } else {
                imageProxy.close()
            }
        }
    }

    // Re-arm scanning when the caller re-enables (e.g. dialog dismissed)
    if (enabled) scanning.set(true)

    androidx.compose.runtime.DisposableEffect(controller) {
        controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
        onDispose { controller.clearImageAnalysisAnalyzer() }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
