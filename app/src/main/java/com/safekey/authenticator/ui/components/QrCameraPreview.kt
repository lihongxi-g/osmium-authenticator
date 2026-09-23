package com.safekey.authenticator.ui.components

import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX + zxing QR scanner shared by the otpauth:// scan screen and the
 * Google Authenticator migration import. Emits raw QR payload strings — the
 * caller decides how to interpret them.
 *
 * Decoding runs on a dedicated single-thread executor with KEEP_ONLY_LATEST
 * backpressure; zxing handles QR codes at any 90-degree orientation, so the
 * frames are decoded as delivered by CameraX.
 *
 * NOTE: the controller MUST be bound to the lifecycle (bindToLifecycle),
 * otherwise it never attaches and the preview stays black.
 */
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
            // 720p analysis is the sweet spot for zxing decode accuracy.
            imageAnalysisTargetSize = CameraController.OutputSize(Size(1280, 720))
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            bindToLifecycle(lifecycleOwner)
        }
    }
    val scanning = remember { AtomicBoolean(true) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val enabledState by rememberUpdatedState(enabled)

    val analyzer = remember {
        ImageAnalysis.Analyzer { imageProxy ->
            if (!enabledState || !scanning.get()) {
                imageProxy.close()
                return@Analyzer
            }
            val raw = try {
                QrDecode.decode(imageProxy)
            } catch (_: Exception) {
                null
            } finally {
                imageProxy.close()
            }
            if (raw != null && scanning.compareAndSet(true, false)) {
                mainHandler.post { onRawCode(raw) }
            }
        }
    }

    // Re-arm scanning when the caller re-enables (e.g. dialog dismissed). This
    // must not happen during composition: an unrelated recomposition re-armed
    // the one-shot latch and the same code could be delivered twice.
    LaunchedEffect(enabled) { if (enabled) scanning.set(true) }

    DisposableEffect(controller) {
        controller.setImageAnalysisAnalyzer(executor, analyzer)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            executor.shutdown()
        }
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
