package com.safekey.authenticator.legal

import android.content.Context
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.security.AppLog
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Fetches the latest Terms of Use and Privacy Policy from osmium.im.
 *
 * Requests are plain GETs for public text. They carry no account,
 * authenticator or device data; the only header added is a User-Agent with
 * the app name and version. Reads happen when the app opens (see
 * MainActivity), throttled to [COOLDOWN_MS] between successful refreshes.
 * Results are held in memory only. The diagnostic log records metadata
 * (document, HTTP status) and never any content.
 */
object LegalDocsRepository {

    const val BASE_URL = "https://osmium.im/"
    private const val COOLDOWN_MS = 10 * 60 * 1000L
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val MAX_CHARS = 200_000

    private val _states =
        MutableStateFlow<Map<LegalDoc, LegalDocState>>(emptyMap())

    /** Latest state per document, observed by the About screen dialogs. */
    val states: StateFlow<Map<LegalDoc, LegalDocState>> = _states.asStateFlow()

    private val lock = Any()
    private var lastSuccessAt = 0L
    private var inFlight = false

    /** True when enough time has passed since the last successful refresh
     *  (a document that was never fetched successfully is always due). */
    fun isDue(lastSuccessAt: Long, now: Long): Boolean =
        lastSuccessAt == 0L || now - lastSuccessAt >= COOLDOWN_MS

    /** Fetches both documents if a successful refresh did not happen recently. */
    suspend fun refreshIfDue(context: Context) = refresh(context, force = false)

    /** Fetches both documents now, bypassing the cooldown (dialog retry). */
    suspend fun refreshNow(context: Context) = refresh(context, force = true)

    fun textUrl(doc: LegalDoc, code: String): String = "$BASE_URL${doc.path}/$code.txt"

    fun pageUrl(doc: LegalDoc, code: String): String = "$BASE_URL${doc.path}/$code/"

    private suspend fun refresh(context: Context, force: Boolean) {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (inFlight) return
            if (!force && !isDue(lastSuccessAt, now)) return
            inFlight = true
        }
        try {
            val code = LegalLang.currentSiteCode(context)
            var anySuccess = false
            withContext(Dispatchers.IO) {
                for (doc in LegalDoc.entries) {
                    val current = _states.value[doc]
                    if (current !is LegalDocState.Loaded) markLoading(doc)
                    val text = fetchDocument(doc, code)
                    if (text != null) {
                        _states.update { it + (doc to LegalDocState.Loaded(text)) }
                        anySuccess = true
                        AppLog.d("legal: ${doc.path} fetched ($code)")
                    } else {
                        if (current !is LegalDocState.Loaded) {
                            _states.update { it + (doc to LegalDocState.Failed) }
                        }
                        AppLog.d("legal: ${doc.path} fetch failed")
                    }
                }
            }
            if (anySuccess) {
                synchronized(lock) { lastSuccessAt = System.currentTimeMillis() }
            }
        } finally {
            synchronized(lock) { inFlight = false }
        }
    }

    private fun markLoading(doc: LegalDoc) {
        _states.update { it + (doc to LegalDocState.Loading) }
    }

    private fun fetchDocument(doc: LegalDoc, code: String): String? {
        fetchText(textUrl(doc, code))?.let { return it }
        // Defensive fallback: if this language is missing, read English.
        if (code != "en") return fetchText(textUrl(doc, "en"))
        return null
    }

    private fun fetchText(url: String): String? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Osmium/${BuildConfig.VERSION_NAME}")
                useCaches = false
            }
        } catch (e: Exception) {
            return null
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                AppLog.d("legal: HTTP ${connection.responseCode} for ${url.removePrefix(BASE_URL)}")
                null
            } else {
                // Bounded read: the limit used to be applied *after* the whole
                // body had been buffered, so a broken or hijacked response
                // could still exhaust memory during the silent refresh on
                // app open.
                val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(8 * 1024)
                    while (true) {
                        val read = reader.read(buffer)
                        if (read < 0) break
                        out.appendRange(buffer, 0, read)
                        if (out.length > MAX_CHARS) return@use null
                    }
                    out.toString()
                } ?: return null
                if (text.isBlank()) null else text
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
