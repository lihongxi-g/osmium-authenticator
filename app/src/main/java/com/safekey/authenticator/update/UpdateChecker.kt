package com.safekey.authenticator.update

import com.safekey.authenticator.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-shot "is there a newer release?" check against the public GitHub API.
 *
 * Called silently when the app opens (at most once per day), only when the
 * user has auto-update checks enabled. No data is sent beyond the standard
 * request — the app never transmits account data or device identifiers.
 * Failures (offline, rate limit, blocked) return null and stay silent.
 */
object UpdateChecker {

    private const val RELEASES_LATEST =
        "https://api.github.com/repos/lihongxi-g/osmium-authenticator/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    @Serializable
    data class LatestRelease(val tag_name: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @return the latest release tag when it is strictly newer than the
     *   installed version, else null.
     */
    fun checkForUpdate(): String? {
        val connection = try {
            (URL(RELEASES_LATEST).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Osmium/${BuildConfig.VERSION_NAME}")
            }
        } catch (e: Exception) {
            return null
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString<LatestRelease>(body)
            val tag = release.tag_name.trim()
            val version = tag.removePrefix("v").removePrefix("V")
            if (isNewer(version, BuildConfig.VERSION_NAME)) tag else null
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Dotted numeric version comparison: true when [candidate] is strictly
     * newer than [current]. Non-numeric or unparseable parts count as 0, so
     * garbage never ranks as an update.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        if (a.isEmpty() || b.isEmpty()) return false
        val length = maxOf(a.size, b.size)
        for (i in 0 until length) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
