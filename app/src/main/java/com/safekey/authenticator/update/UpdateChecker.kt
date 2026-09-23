package com.safekey.authenticator.update

import com.safekey.authenticator.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/** A newer release plus the changelog GitHub publishes with it. */
data class UpdateInfo(
    /** Git tag, e.g. "v2.5.2". */
    val tag: String,
    /** Release notes as published (Markdown; the UI cleans them for display). */
    val notes: String,
    /** Release page to open in the browser. */
    val url: String
)

/** Outcome of one check, so the UI can tell "up to date" from "offline". */
sealed interface UpdateResult {
    data class Found(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

/**
 * One-shot "is there a newer release?" check against the public GitHub API.
 *
 * Called silently when the app opens (when the user has auto-update checks
 * enabled) and on demand from About → check for updates. No data is sent
 * beyond the standard request — the app never transmits account data or
 * device identifiers. Failures (offline, rate limit, blocked) come back as
 * [UpdateResult.Failed] and stay silent in the UI.
 */
object UpdateChecker {

    private const val RELEASES_LATEST =
        "https://api.github.com/repos/lihongxi-g/osmium-authenticator/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/lihongxi-g/osmium-authenticator/releases"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    @Serializable
    private data class LatestRelease(
        val tag_name: String = "",
        val body: String = "",
        val html_url: String = ""
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Single request to `releases/latest` (same payload carries tag, notes and
     * page URL). Never throws: null means offline / rate limited / blocked.
     * Also used by the developer-mode dialog preview, which shows the notes of
     * the newest release whether or not it is newer than this build.
     */
    fun fetchLatest(): UpdateInfo? {
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
            if (tag.isEmpty()) return null
            UpdateInfo(
                tag = tag,
                notes = release.body,
                url = release.html_url.trim().ifBlank { RELEASES_PAGE }
            )
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /** Is the newest published release newer than the installed build? */
    fun check(): UpdateResult {
        val info = fetchLatest() ?: return UpdateResult.Failed
        val version = info.tag.removePrefix("v").removePrefix("V")
        return if (isNewer(version, BuildConfig.VERSION_NAME)) {
            UpdateResult.Found(info)
        } else {
            UpdateResult.UpToDate
        }
    }

    /**
     * Dotted numeric version comparison: true when [candidate] is strictly
     * newer than [current]. Non-numeric or unparseable parts count as 0, so
     * garbage never ranks as an update.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        // One comparable entry per dot-separated segment, in position: dropping
        // non-numeric parts (mapNotNull) shifted every later segment, so
        // "2.5.3-fix1" parsed as [2,5] and never ranked as newer than 2.5.2.
        val a = candidate.split('.').map { leadingNumber(it) }
        val b = current.split('.').map { leadingNumber(it) }
        if (a.isEmpty() || b.isEmpty()) return false
        val length = maxOf(a.size, b.size)
        for (i in 0 until length) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    /** Leading digits of a segment ("3-fix1" → 3); no digits counts as 0. */
    private fun leadingNumber(segment: String): Int {
        val digits = segment.trim().takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}
