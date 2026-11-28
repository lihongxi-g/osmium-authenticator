package com.safekey.authenticator.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.icons.IconLimits
import com.safekey.authenticator.icons.IconRenderer
import com.safekey.authenticator.ui.components.SimpleTopBar
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Square-crop step for a gallery image used as an account icon.
 *
 * The preview is circular because that is how the icon will be drawn in the
 * list; the crop itself stays square so the same bitmap also works as a pack
 * icon elsewhere. Pinch zooms (up to 5x of the cover scale), drag moves the
 * image, and the visible circle is exactly what gets stored.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconCropScreen(
    vm: MainViewModel,
    accountId: String,
    uri: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember(uri) { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var viewportPx by remember { mutableStateOf(0) }
    var scale by remember { mutableStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                val parsed = Uri.parse(uri)
                val stream = context.contentResolver.openInputStream(parsed)
                    ?: return@withContext null
                val bytes = stream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > IconLimits.MAX_CUSTOM_SOURCE_BYTES) return@withContext null
                        out.write(buffer, 0, read)
                    }
                    out.toByteArray()
                }
                IconRenderer.decodeForCrop(bytes, context.contentResolver.getType(parsed))
            } catch (e: OutOfMemoryError) {
                null
            } catch (e: Exception) {
                null
            }
        }
        if (loaded == null) loadFailed = true else bitmap = loaded
    }

    // Centre the image and make the shorter edge cover the circular viewport.
    LaunchedEffect(bitmap, viewportPx) {
        val image = bitmap ?: return@LaunchedEffect
        if (viewportPx <= 0) return@LaunchedEffect
        val base = viewportPx.toFloat() / min(image.width, image.height)
        scale = base
        offset = Offset(
            (viewportPx - image.width * base) / 2f,
            (viewportPx - image.height * base) / 2f
        )
    }

    fun save() {
        val image = bitmap ?: return
        if (scale <= 0f || viewportPx <= 0 || saving) return
        saving = true
        scope.launch {
            val png = withContext(Dispatchers.Default) {
                cropToPng(image, offset, scale, viewportPx)
            }
            if (png == null) {
                saving = false
                vm.showToast(context.getString(R.string.icon_custom_failed))
            } else {
                vm.setAccountIconFromPng(accountId, png) { ok ->
                    saving = false
                    if (ok) onSaved()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            SimpleTopBar(
                title = stringResource(R.string.icon_crop_title),
                onBack = onBack,
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = bitmap != null && !saving
                    ) {
                        Text(stringResource(R.string.icon_crop_done))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                loadFailed -> {
                    Text(
                        text = stringResource(R.string.icon_custom_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                bitmap == null -> {
                    CircularProgressIndicator()
                }
                else -> {
                    val source = bitmap!!
                    val image = source.asImageBitmap()
                    Box(
                        modifier = Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .onSizeChanged { viewportPx = it.width }
                                .pointerInput(bitmap, viewportPx) {
                                    detectTransformGestures { centroid, pan, zoom, _ ->
                                        val source = bitmap ?: return@detectTransformGestures
                                        if (viewportPx <= 0 || scale <= 0f) {
                                            return@detectTransformGestures
                                        }
                                        val minScale = viewportPx.toFloat() /
                                            min(source.width, source.height)
                                        val next = (scale * zoom)
                                            .coerceIn(minScale, minScale * 5f)
                                        val factor = next / scale
                                        val moved = Offset(
                                            centroid.x + (offset.x - centroid.x) * factor + pan.x,
                                            centroid.y + (offset.y - centroid.y) * factor + pan.y
                                        )
                                        offset = clampOffset(moved, source, next, viewportPx)
                                        scale = next
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawImage(
                                    image = image,
                                    srcOffset = IntOffset.Zero,
                                    srcSize = IntSize(
                                        source.width,
                                        source.height
                                    ),
                                    dstOffset = IntOffset(
                                        offset.x.roundToInt(),
                                        offset.y.roundToInt()
                                    ),
                                    dstSize = IntSize(
                                        (source.width * scale).roundToInt(),
                                        (source.height * scale).roundToInt()
                                    ),
                                    filterQuality = FilterQuality.High
                                )
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.45f),
                                    radius = size.minDimension / 2f - 2f,
                                    style = Stroke(width = 2f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.icon_crop_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** Keeps the image covering the viewport; negative offsets only, by design. */
private fun clampOffset(
    candidate: Offset,
    image: Bitmap,
    scale: Float,
    viewportPx: Int
): Offset {
    val minX = viewportPx - image.width * scale
    val minY = viewportPx - image.height * scale
    return Offset(
        candidate.x.coerceIn(min(minX, 0f), 0f),
        candidate.y.coerceIn(min(minY, 0f), 0f)
    )
}

/**
 * Cuts the visible square out of [source] and returns a [IconLimits.STORE_PX]
 * PNG. [offset] is the image's top-left in viewport pixels and [scale] maps
 * source pixels to viewport pixels.
 */
private fun cropToPng(
    source: Bitmap,
    offset: Offset,
    scale: Float,
    viewportPx: Int
): ByteArray? {
    if (scale <= 0f || viewportPx <= 0) return null
    val wanted = (viewportPx / scale).roundToInt().coerceAtLeast(1)
    val left = (-offset.x / scale).roundToInt().coerceIn(0, (source.width - 1).coerceAtLeast(0))
    val top = (-offset.y / scale).roundToInt().coerceIn(0, (source.height - 1).coerceAtLeast(0))
    val maxSide = min(source.width - left, source.height - top).coerceAtLeast(1)
    val side = wanted.coerceAtMost(maxSide)
    var cropped: Bitmap? = null
    return try {
        val square = Bitmap.createBitmap(source, left, top, side, side)
        cropped = square
        val target = if (square.width == IconLimits.STORE_PX) square else {
            Bitmap.createScaledBitmap(square, IconLimits.STORE_PX, IconLimits.STORE_PX, true)
        }
        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.PNG, 100, out)
        if (target !== square) target.recycle()
        out.toByteArray()
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    } finally {
        cropped?.recycle()
    }
}
