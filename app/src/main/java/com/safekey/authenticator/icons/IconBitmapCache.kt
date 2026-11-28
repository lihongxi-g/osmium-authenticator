package com.safekey.authenticator.icons

import android.graphics.Bitmap
import android.util.LruCache
import java.security.MessageDigest

/**
 * Decoded-icon cache shared by the account list, the picker grid and the
 * account form.
 *
 * Keys are content hashes, not [ByteArray] identities: the repository produces
 * a fresh array on every database emission (copying a code re-emits the flow),
 * so identity keys would re-decode the whole list on every copy.
 */
object IconBitmapCache {

    private const val MAX_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun key(bytes: ByteArray, px: Int): String = "${sha256(bytes)}@$px"

    /** Synchronous lookup (no decode), for a composition's first frame. */
    fun peek(key: String): Bitmap? = cache.get(key)

    /** Cached decode for on-screen use (square, circle-masked). Safe off-main. */
    fun getOrDecode(bytes: ByteArray, mime: String?, px: Int): Bitmap? {
        val key = key(bytes, px)
        cache.get(key)?.let { return it }
        val bitmap = IconRenderer.decodeForDisplay(bytes, mime, px) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    fun clear() = cache.evictAll()

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val out = StringBuilder(digest.size * 2)
        for (b in digest) {
            val value = b.toInt() and 0xFF
            out.append(Character.forDigit(value ushr 4, 16))
            out.append(Character.forDigit(value and 0x0F, 16))
        }
        return out.toString()
    }
}
