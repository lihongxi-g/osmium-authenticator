package com.safekey.authenticator.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.media.ExifInterface
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders pack icons and entry icons to bitmaps.
 *
 * Everything here is CPU-bound and must run off the main thread. Results are
 * bounded by [IconLimits]: decoding is downsampled with inSampleSize so even a
 * 48 MP gallery image fits in bounded memory, and every render is capped at the
 * requested size.
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

    /**
     * Decodes an icon for on-screen display at exactly [px]×[px].
     *
     * Two things happen here that must not happen per frame:
     *  - a centre-crop to a square, so `ContentScale.Crop` never resamples the
     *    bitmap while a list scrolls;
     *  - an anti-aliased circular mask baked into the pixels, instead of a
     *    `Modifier.clip(CircleShape)` draw-time clip on every row.
     *
     * A draw-time clip on a bitmap edge is what made list icons shimmer and
     * look soft while scrolling on Android 16: the clipped texture was
     * resampled every frame. With the mask in the bitmap the row is a plain
     * 1:1 bitmap draw.
     */
    fun decodeForDisplay(bytes: ByteArray, mime: String?, px: Int): Bitmap? {
        val size = px.coerceIn(16, IconLimits.MAX_SOURCE_PX)
        val decoded = decode(bytes, mime, size) ?: return null
        return try {
            circleMask(centerSquare(decoded, size))
        } catch (e: OutOfMemoryError) {
            decoded.recycle()
            null
        } catch (e: Exception) {
            decoded.recycle()
            null
        }
    }

    /**
     * Decodes a gallery image for the crop screen. Unlike [decode], this keeps
     * up to [IconLimits.CROP_SOURCE_PX] on the long edge and applies the EXIF
     * orientation: phones write camera photos rotated with an orientation tag,
     * and BitmapFactory ignores it. Both are needed for the crop rectangle the
     * user sees to match the pixels that are cut out.
     */
    fun decodeForCrop(bytes: ByteArray, mime: String?): Bitmap? {
        if (bytes.isEmpty()) return null
        return try {
            if (isSvg(bytes, mime)) {
                renderSvg(bytes, IconLimits.CROP_SOURCE_PX)
            } else {
                val decoded = decodeRasterForCrop(bytes, IconLimits.CROP_SOURCE_PX) ?: return null
                applyExifOrientation(decoded, bytes)
            }
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downsample-first decode for cropping. The sampling loop targets
     * [maxPx] directly (the icon path deliberately ends up at least 2x its
     * slot first for quality); at 1600 px this keeps a 12 MP photo around
     * 16 MB instead of decoding it at full 48 MB.
     */
    private fun decodeRasterForCrop(bytes: ByteArray, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null
        if (width > 100_000 || height > 100_000) return null

        var sample = 1
        while (max(width, height) / (sample * 2) >= maxPx) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        if (decoded.width <= maxPx && decoded.height <= maxPx) return decoded

        val scale = maxPx.toFloat() / max(decoded.width, decoded.height)
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceIn(1, maxPx),
            (decoded.height * scale).roundToInt().coerceIn(1, maxPx),
            true
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    private fun applyExifOrientation(src: Bitmap, bytes: ByteArray): Bitmap {
        val orientation = try {
            ExifInterface(ByteArrayInputStream(bytes))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: Exception) {
            return src
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return src
        }
        return try {
            val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            if (rotated !== src) src.recycle()
            rotated
        } catch (e: OutOfMemoryError) {
            src
        }
    }

    private fun centerSquare(src: Bitmap, size: Int): Bitmap {
        var current = src
        val side = min(current.width, current.height)
        if (current.width != side || current.height != side) {
            val cropped = Bitmap.createBitmap(
                current,
                (current.width - side) / 2,
                (current.height - side) / 2,
                side,
                side
            )
            if (cropped !== current) {
                current.recycle()
                current = cropped
            }
        }
        if (current.width != size) {
            val scaled = Bitmap.createScaledBitmap(current, size, size, true)
            if (scaled !== current) {
                current.recycle()
                current = scaled
            }
        }
        return current
    }

    private fun circleMask(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val radius = src.width / 2f
        val path = Path().apply { addCircle(radius, radius, radius, Path.Direction.CW) }
        canvas.clipPath(path)
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return out
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
        // Huge images are not rejected outright: inSampleSize bounds the
        // allocation, so a 48 MP gallery photo still becomes a 256 px icon.
        // Only dimensions that cannot be a real image are refused.
        if (width > 100_000 || height > 100_000) return null

        var sample = 1
        while (max(width, height) / (sample * 2) >= px * 2) sample *= 2
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
