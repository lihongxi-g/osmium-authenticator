package com.safekey.authenticator.ui.components

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * QR decoding on top of zxing core — the same pure-Java library already used
 * for QR generation. Replaces the closed-source ML Kit scanner so the app
 * (and its F-Droid build) stays 100% free software and offline.
 *
 * zxing detects QR codes in any of the four 90-degree orientations, so
 * frames are decoded exactly as delivered by CameraX (no rotation pass).
 */
object QrDecode {

    private val HINTS: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    /** Decodes the Y (luminance) plane of a CameraX analysis frame. */
    fun decode(image: ImageProxy): String? {
        if (image.planes.isEmpty()) return null
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val width = image.width
        val height = image.height
        val rowStride = plane.rowStride
        val data = ByteArray(width * height)
        if (rowStride == width) {
            val len = minOf(buffer.remaining(), data.size)
            buffer.get(data, 0, len)
        } else {
            val row = ByteArray(rowStride)
            var out = 0
            for (y in 0 until height) {
                val n = minOf(rowStride, buffer.remaining())
                if (n <= 0) break
                buffer.get(row, 0, n)
                System.arraycopy(row, 0, data, out, minOf(width, n))
                out += width
            }
        }
        return decodeLuminance(data, width, height)
    }

    /** Decodes a bitmap from the gallery (screenshot or photo, any rotation). */
    fun decode(bitmap: Bitmap): String? = try {
        val scaled = downscale(bitmap)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        decodePixels(pixels, width, height)
    } catch (_: Exception) {
        null
    }

    /** Runs [decode] off the main thread and delivers the result back on it. */
    fun decodeAsync(bitmap: Bitmap, onResult: (String?) -> Unit) {
        Thread {
            val raw = decode(bitmap)
            Handler(Looper.getMainLooper()).post { onResult(raw) }
        }.start()
    }

    internal fun decodeLuminance(data: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false)
        return decodeSource(source) ?: decodeSource(source.invert())
    }

    internal fun decodePixels(pixels: IntArray, width: Int, height: Int): String? {
        val source = RGBLuminanceSource(width, height, pixels)
        return decodeSource(source) ?: decodeSource(source.invert())
    }

    private fun decodeSource(source: LuminanceSource): String? = try {
        val reader = MultiFormatReader()
        reader.setHints(HINTS)
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: Exception) {
        null
    }

    /** Caps the decode input at 1920px on the longest side to bound memory. */
    private fun downscale(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private const val MAX_DIMENSION = 1920
}
