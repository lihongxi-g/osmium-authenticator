package com.safekey.authenticator.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the zxing swap: the camera (luminance) and gallery (RGB) decode
 * paths must read QR codes in any of the four 90-degree orientations.
 */
class QrDecodeTest {

    private val content =
        "otpauth://totp/Example:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=Example"

    private fun qrLuminance(size: Int): ByteArray {
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val data = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                data[y * size + x] = (if (matrix.get(x, y)) 0 else 0xFF).toByte()
            }
        }
        return data
    }

    private fun rotate(data: ByteArray, w: Int, h: Int, degrees: Int): Pair<ByteArray, Pair<Int, Int>> {
        when (degrees) {
            0 -> return data.copyOf() to (w to h)
            180 -> {
                val out = ByteArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        out[y * w + x] = data[(h - 1 - y) * w + (w - 1 - x)]
                    }
                }
                return out to (w to h)
            }
            else -> {
                val ow = h
                val out = ByteArray(w * h)
                for (y in 0 until w) {
                    for (x in 0 until ow) {
                        out[y * ow + x] = if (degrees == 90) {
                            data[(h - 1 - x) * w + y]
                        } else {
                            data[x * w + (w - 1 - y)]
                        }
                    }
                }
                return out to (ow to w)
            }
        }
    }

    @Test
    fun decodesCameraFramesInAllFourOrientations() {
        val size = 240
        val base = qrLuminance(size)
        for (deg in intArrayOf(0, 90, 180, 270)) {
            val (data, dims) = rotate(base, size, size, deg)
            assertEquals(
                "rotation $deg",
                content,
                QrDecode.decodeLuminance(data, dims.first, dims.second)
            )
        }
    }

    @Test
    fun decodesGalleryPixelsWithRotation() {
        val size = 240
        val (data, dims) = rotate(qrLuminance(size), size, size, 90)
        val pixels = IntArray(data.size) { i ->
            val v = data[i].toInt() and 0xFF
            0xFF000000.toInt() or (v shl 16) or (v shl 8) or v
        }
        assertEquals(content, QrDecode.decodePixels(pixels, dims.first, dims.second))
    }

    @Test
    fun blankFrameYieldsNoResult() {
        val blank = ByteArray(320 * 240) { 0xFF.toByte() }
        assertNull(QrDecode.decodeLuminance(blank, 320, 240))
    }
}
