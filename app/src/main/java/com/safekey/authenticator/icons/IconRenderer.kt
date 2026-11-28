package com.safekey.authenticator.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Renders pack icons and entry icons to bitmaps.
 *
 * Everything here is CPU-bound and must run off the main thread. Results are
 * bounded by [IconLimits]: source bitmaps are rejected above 4096 px per edge
 * (decompression bombs) and every render is capped at the requested size.
 */
object IconRenderer {

    /** A rasterised PNG suitable for encrypted storage. */
    data class StoredIcon(val bytes: ByteArray, val mime: String)

    fun isSvg(bytes: ByteArray, mime: String?): Boolean {
        if (IconType.fromMime(mime) == IconType.SVG) return true
        // Sniff the first bytes too: packs may declare .svg with a wrong MIME.
        val head = String(bytes, 0, minOf(bytes.size, 256), Charsets.UTF_8)
        return head.contains("<svg", ignoreCase = true)
    }

    /** Decodes [bytes] to at most [maxPx] on the longer edge. Never throws. */
    fun decode(bytes: ByteArray, mime: String?, maxPx: Int = IconLimits.STORE_PX): Bitmap? {
        val px = maxPx.coerceIn(16, IconLimits.MAX_SOURCE_PX)
        if (bytes.isEmpty()) return null
        return try {
            if (isSvg(bytes, mime)) renderSvg(bytes, px) else decodeRaster(bytes, px)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Rasterises an icon to [IconLimits.STORE_PX] and re-encodes it as PNG.
     * Storing raster PNGs keeps list rendering to plain BitmapFactory work and
     * makes exports to Aegis readable everywhere. Returns null when the input
     * is not a usable icon or the result would exceed the storage cap.
     */
    fun prepareForStorage(bytes: ByteArray, mime: String?): StoredIcon? {
        val bitmap = decode(bytes, mime, IconLimits.STORE_PX) ?: return null
        val output = ByteArrayOutputStream()
        return try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            val png = output.toByteArray()
            if (png.isEmpty() || png.size > IconLimits.MAX_STORED_BYTES) null
            else StoredIcon(png, "image/png")
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderSvg(bytes: ByteArray, px: Int): Bitmap? {
        val svg = SVG.getFromInputStream(ByteArrayInputStream(bytes))
        var width = svg.documentWidth
        var height = svg.documentHeight
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) {
            // Some packs omit width/height but do carry a viewBox.
            val viewBox = svg.documentViewBox
            width = viewBox?.width() ?: 0f
            height = viewBox?.height() ?: 0f
        }
        if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null

        val scale = px / max(width, height)
        val outWidth = (width * scale).roundToInt().coerceIn(1, px)
        val outHeight = (height * scale).roundToInt().coerceIn(1, px)
        val picture = svg.renderToPicture(outWidth, outHeight)
        val bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPicture(picture)
        return bitmap
    }

    private fun decodeRaster(bytes: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        // Refuse absurd dimensions before allocating anything: a 30000×30000
        // PNG is a memory bomb even with inSampleSize.
        if (width > 4096 || height > 4096) return null

        var sample = 1
        while (width / (sample * 2) >= px && height / (sample * 2) >= px) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (decoded.width <= px && decoded.height <= px) return decoded

        val scale = px.toFloat() / max(decoded.width, decoded.height)
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceIn(1, px),
            (decoded.height * scale).roundToInt().coerceIn(1, px),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
