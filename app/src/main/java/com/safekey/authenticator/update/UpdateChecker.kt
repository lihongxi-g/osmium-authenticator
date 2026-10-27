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
    val url: String,
    /** Published as a GitHub pre-release (beta / preview build). */
    val isPreview: Boolean = false
)

/** Outcome of one check, so the UI can tell "up to date" from "offline". */
sealed interface UpdateResult {
    data class Found(val info: UpdateInfo) : UpdateResult
    data object UpToDate : UpdateResult
    data object Failed : UpdateResult
}

/**
 * One published release before the channel filter runs. Kept separate from
 * [UpdateInfo] so the channel choice can be unit-tested without a network.
 */
data class UpdateCandidate(
    val tag: String,
    val notes: String,
    val url: String,
    /** GitHub's `prerelease` flag — the build was published as a preview. */
    val isPreview: Boolean
)

/**
 * One-shot "is there a newer release?" check against the public GitHub API.
 *
 * Called silently when the app opens (each enabled channel) and on demand from
 * About → check for updates and from Settings → update checks. No data is sent
 * beyond the standard request — the app never transmits account data or
 * device identifiers. Failures (offline, rate limit, blocked) come back as
 * [UpdateResult.Failed] and stay silent in the UI.
 *
 * Two channels are offered, each behind its own switch:
 *  - stable: regular releases, read from `releases/latest` (one small request);
 *  - preview: releases published as pre-releases (beta and test builds), read
 *    from the release list. Off unless the user turned it on.
 */
object UpdateChecker {

    private const val RELEASES_LATEST =
        "https://api.github.com/repos/lihongxi-g/osmium-authenticator/releases/latest"
    /** Pre-releases never appear in `releases/latest`, so they need the list. */
    private const val RELEASES_LIST =
        "https://api.github.com/repos/lihongxi-g/osmium-authenticator/releases?per_page=30"
    private const val RELEASES_PAGE =
        "https://github.com/lihongxi-g/osmium-authenticator/releases"
    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000

    /** Payload of one release; every field defaults so a field rename cannot crash us. */
    @Serializable
    private data class Release(
        val tag_name: String = "",
        val body: String = "",
        val html_url: String = "",
        val prerelease: Boolean = false,
        val draft: Boolean = false
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * GET a JSON body from the API, or null when offline / rate limited /
     * blocked. Never throws.
     */
    private fun get(url: String): String? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
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
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Newest regular release (same payload carries tag, notes and page URL).
     * Also used by the developer-mode dialog preview, which shows the notes of
     * the newest release whether or not it is newer than this build.
     */
    fun fetchLatest(): UpdateInfo? {
        val body = get(RELEASES_LATEST) ?: return null
        val release = try {
            json.decodeFromString<Release>(body)
        } catch (e: Exception) {
            return null
        }
        // `releases/latest` never returns a pre-release, so this is stable.
        return release.toInfo()?.copy(isPreview = false)
    }

    /**
     * Every published release, pre-releases included (drafts and entries
     * without a tag are dropped). Null when the request failed.
     */
    private fun fetchReleases(): List<UpdateCandidate>? {
        val body = get(RELEASES_LIST) ?: return null
        val releases = try {
            json.decodeFromString<List<Release>>(body)
        } catch (e: Exception) {
            return null
        }
        return releases
            .filter { !it.draft }
            .mapNotNull { it.toInfo() }
            .map {
                UpdateCandidate(
                    tag = it.tag,
                    notes = it.notes,
                    url = it.url,
                    isPreview = it.isPreview
                )
            }
    }

    /** Tag/notes/URL of a release payload; null when the tag is missing. */
    private fun Release.toInfo(): UpdateInfo? {
        val cleanTag = tag_name.trim()
        if (cleanTag.isEmpty()) return null
        return UpdateInfo(
            tag = cleanTag,
            notes = body,
            url = html_url.trim().ifBlank { RELEASES_PAGE },
            isPreview = prerelease
        )
    }

    /**
     * Highest version among the releases the enabled channels offer, or null
     * when they offer nothing. Pre-releases only count while the preview
     * channel is on, and they still rank below the same version number
     * published as a regular release (see [isNewer]).
     */
    fun newestForChannels(
        candidates: List<UpdateCandidate>,
        includeStable: Boolean,
        includePreview: Boolean
    ): UpdateInfo? {
        val offered = candidates.filter { if (it.isPreview) includePreview else includeStable }
        val newest = offered.reduceOrNull { best, next ->
            if (compareVersions(parseVersion(next.tag), parseVersion(best.tag)) > 0) next else best
        } ?: return null
        return UpdateInfo(
            tag = newest.tag,
            notes = newest.notes,
            url = newest.url,
            isPreview = newest.isPreview
        )
    }

    /**
     * Is there a newer build in the enabled channels? [includeStable] and
     * [includePreview] mirror the two switches in Settings.
     *
     * With the preview channel off this asks `releases/latest` — the same
     * single request the app has always made. Only a preview-enabled check
     * pulls the release list, because pre-releases are invisible to
     * `releases/latest`.
     */
    fun check(includeStable: Boolean = true, includePreview: Boolean = false): UpdateResult {
        val info = when {
            includePreview -> newestForChannels(
                candidates = fetchReleases() ?: return UpdateResult.Failed,
                includeStable = includeStable,
                includePreview = true
            )

            includeStable -> fetchLatest()

            // Neither channel enabled: no request at all. A build nobody asked
            // to check must not phone home (F-Droid review: no connections
            // unless the user opted in).
            else -> return UpdateResult.UpToDate
        } ?: return UpdateResult.UpToDate

        return if (isNewer(info.tag, BuildConfig.VERSION_NAME)) {
            UpdateResult.Found(info)
        } else {
            UpdateResult.UpToDate
        }
    }

    /**
     * True when [candidate] is strictly newer than [current].
     *
     * Dotted numeric comparison plus a pre-release rank: non-numeric or
     * unparseable parts count as 0, so garbage never ranks as an update, and
     * "2.6.1-beta" sorts below the released "2.6.1" — a preview user must be
     * offered the final build of the version they are testing, which the
     * numeric-only compare used to swallow.
     */
    fun isNewer(candidate: String, current: String): Boolean =
        compareVersions(parseVersion(candidate), parseVersion(current)) > 0

    /** Numeric part plus the pre-release identifiers after the first "-". */
    private data class Version(val numbers: List<Int>, val preRelease: List<String>)

    private fun parseVersion(raw: String): Version {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        // One comparable entry per dot-separated segment, in position: dropping
        // non-numeric parts shifted every later segment, so "2.5.3-fix1"
        // parsed as [2,5] and never ranked as newer than 2.5.2.
        val numbers = trimmed.substringBefore('-').split('.').map { leadingNumber(it) }
        val preRelease = trimmed.substringAfter('-', "")
            .split('.', '-')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return Version(numbers, preRelease)
    }

    /** -1 / 0 / +1, semver-style: numbers first, then the pre-release rank. */
    private fun compareVersions(a: Version, b: Version): Int {
        val length = maxOf(a.numbers.size, b.numbers.size)
        for (i in 0 until length) {
            val left = a.numbers.getOrElse(i) { 0 }
            val right = b.numbers.getOrElse(i) { 0 }
            if (left != right) return if (left > right) 1 else -1
        }
        return when {
            // Same numbers: no suffix means the released build, so it outranks
            // the pre-release of that same version.
            a.preRelease.isEmpty() && b.preRelease.isEmpty() -> 0
            a.preRelease.isEmpty() -> 1
            b.preRelease.isEmpty() -> -1
            else -> comparePreRelease(a.preRelease, b.preRelease)
        }
    }

    /**
     * Pre-release identifiers, left to right: numeric qualifies below
     * alphanumeric, numbers compare as numbers, and a shorter identifier list
     * ranks below a longer one that starts the same ("beta" < "beta.2").
     */
    private fun comparePreRelease(a: List<String>, b: List<String>): Int {
        val length = minOf(a.size, b.size)
        for (i in 0 until length) {
            val left = a[i]
            val right = b[i]
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val order = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
            if (order != 0) return if (order > 0) 1 else -1
        }
        return a.size.compareTo(b.size)
    }

    /** Leading digits of a segment ("3-fix1" → 3); no digits counts as 0. */
    private fun leadingNumber(segment: String): Int {
        val digits = segment.trim().takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }
}
