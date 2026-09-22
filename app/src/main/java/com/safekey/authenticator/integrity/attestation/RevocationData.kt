package com.safekey.authenticator.integrity.attestation

import android.content.Context
import com.android.keyattestation.verifier.parseAttestationStatus
import com.safekey.authenticator.security.AppLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline-first revocation data.
 *
 * The app ships a build-time snapshot of Google's revocation status list
 * (assets/attestation_status.json — refreshed by CI from
 * https://android.googleapis.com/attestation/status). A user-triggered
 * refresh downloads a newer copy and atomically stores it in filesDir; the
 * previous data stays in use on any failure.
 *
 * Network policy (by design): nothing here ever blocks verification, reports
 * an error state, or marks the device suspicious. A failed refresh only
 * surfaces an informational notice — for users behind restricted networks
 * the built-in snapshot keeps working forever.
 */
internal object RevocationData {

    const val STATUS_URL = "https://android.googleapis.com/attestation/status"

    private const val ASSET_NAME = "attestation_status.json"
    private const val CACHE_NAME = "attestation_status_cache.json"
    private const val CONNECT_TIMEOUT_MS = 3_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_BYTES = 4 * 1024 * 1024

    @Volatile private var cached: Set<String>? = null

    /** Full revoked-serial set (refreshed cache file if present, else the bundled asset). */
    fun current(context: Context): Set<String> {
        cached?.let { return it }
        // Normalized once per process: the snapshot mixes decimal and
        // zero-padded hex renderings of the same serials while the verifier
        // looks up serialNumber.toString(16) — without this, more than half of
        // the revoked keys could never match a certificate (see
        // RevocationSerials).
        val loaded = RevocationSerials.normalize(load(context))
        cached = loaded
        return loaded
    }

    private fun load(context: Context): Set<String> {
        val cacheFile = File(context.filesDir, CACHE_NAME)
        try {
            if (cacheFile.exists()) {
                return cacheFile.inputStream().use { parseAttestationStatus(it) }
            }
        } catch (e: Exception) {
            AppLog.detection("revocation cache unreadable: ${e.javaClass.simpleName}")
        }
        return try {
            context.assets.open(ASSET_NAME).use { parseAttestationStatus(it) }
        } catch (e: Exception) {
            AppLog.detection("revocation asset unreadable: ${e.javaClass.simpleName}")
            emptySet()
        }
    }

    /** Human-readable date of the data currently in use, best effort. */
    fun currentDateLabel(context: Context): String? {
        val cacheFile = File(context.filesDir, CACHE_NAME)
        val at = cacheFile.takeIf { it.exists() }?.lastModified() ?: return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(at))
    }

    sealed interface RefreshResult {
        /** Downloaded, validated and stored [count] revoked serials. */
        data class Updated(val count: Int) : RefreshResult

        /** Any network / HTTP problem — the previous data stays in use. */
        data class Failed(val reason: String) : RefreshResult
    }

    /**
     * Downloads a fresh status list and stores it atomically. Blocking; call
     * from a background dispatcher. Never throws.
     */
    fun refresh(context: Context): RefreshResult {
        return try {
            val payload = download(STATUS_URL)
            // Validate before replacing anything in use.
            val count = payload.inputStream().use { parseAttestationStatus(it) }.size
            if (count < 1) return RefreshResult.Failed("empty status list")
            val target = File(context.filesDir, CACHE_NAME)
            val tmp = File(context.filesDir, "$CACHE_NAME.tmp")
            tmp.writeBytes(payload)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) return RefreshResult.Failed("storage failure")
            }
            cached = null // reload on next use
            AppLog.detection("revocation data updated: $count entries")
            RefreshResult.Updated(count)
        } catch (e: Exception) {
            AppLog.detection("revocation refresh failed: ${e.javaClass.simpleName}")
            RefreshResult.Failed(e.javaClass.simpleName)
        }
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_BYTES) throw IOException("payload too large")
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
