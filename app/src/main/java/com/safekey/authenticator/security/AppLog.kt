package com.safekey.authenticator.security

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Diagnostic log: in-memory ring buffer (last ~100 lines) **plus an on-disk
 * copy that survives the process being killed**, and crash persistence.
 * Export copies device info plus the log to the clipboard — no permissions, no
 * network, nothing leaves the device unless the user pastes it somewhere.
 *
 * The on-disk copy exists because of something the user could not report
 * otherwise: the whole scheduled-backup path ([com.safekey.authenticator.backup.AutoBackupReceiver],
 * [com.safekey.authenticator.backup.AutoBackupWorker]) runs while no activity is
 * up, and on phones that kill a swiped-away app the ring buffer died with the
 * process — "the nightly backup did nothing" was unverifiable, because every
 * trace of it was gone by the time the app was opened again.
 *
 * Appends go through one writer thread, so logging never blocks the main thread.
 */
object AppLog {

    private const val MAX_LINES = 100

    /** Roll the on-disk file at this size: it is a diagnostic tail, not an archive. */
    private const val LIVE_MAX_BYTES = 256 * 1024

    /** How much of the on-disk file an export shows. */
    private const val EXPORT_LINES = 250

    private val buffer = ArrayDeque<String>(MAX_LINES + 8)
    private val lock = Any()
    private var logFile: File? = null
    private var liveFile: File? = null

    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "applog-writer").apply { isDaemon = true }
    }

    fun init(context: Context) {
        logFile = File(context.filesDir, "safekey-log.txt")
        liveFile = File(context.filesDir, "safekey-log-live.txt")
        // carry over the previous session's crash log (if any)
        // Defensive: this runs inside Application.onCreate, so an IO failure
        // here would crash every single launch (and the crash handler recreates
        // the file, turning it into a permanent crash loop).
        val prev = logFile?.takeIf { it.exists() }?.let { file ->
            runCatching { file.readText() }.getOrNull()
        }
        if (!prev.isNullOrBlank()) {
            d("── previous session crash log ──")
            // The crash records sit at the END of the file (the ring buffer is
            // persisted chronologically), so restore the tail — taking the head
            // discarded exactly the part this file exists for.
            prev.lines().takeLast(MAX_LINES / 2).forEach { dRaw(it) }
            runCatching { logFile?.delete() }
        }
    }

    fun d(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        dRaw("$time $message")
    }

    /**
     * Detection logging is off by default: device-integrity results must not
     * end up in the log unless the developer-mode "detailed logging" switch
     * is on (AppSettings.devDetailedLogging drives this flag).
     */
    @Volatile var detectionLoggingEnabled = false

    fun detection(message: String) {
        if (detectionLoggingEnabled) d(message)
    }

    private fun dRaw(line: String) {
        synchronized(lock) {
            buffer.addLast(line)
            while (buffer.size > MAX_LINES) buffer.removeFirst()
        }
        appendToDisk(line)
    }

    /**
     * Append one line to the on-disk copy. Failures are swallowed on purpose: a
     * full disk must never disturb logging (which is what would report it).
     */
    private fun appendToDisk(line: String) {
        val file = liveFile ?: return
        runCatching {
            writer.execute {
                runCatching {
                    if (file.length() > LIVE_MAX_BYTES) {
                        // Keep the recent tail so the file cannot grow forever.
                        val tail = file.readLines().takeLast(MAX_LINES * 4)
                        file.writeText(tail.joinToString("\n") + "\n")
                    }
                    file.appendText(line + "\n")
                }
            }
        }
    }

    fun snapshot(): List<String> = synchronized(lock) { buffer.toList() }

    /** Call from the crash handler — best effort persist. */
    fun persistCrash() {
        val f = logFile ?: return
        try {
            f.writeText(snapshot().joinToString("\n"))
        } catch (_: Exception) {
        }
    }

    /**
     * Everything a report needs: device info plus the on-disk log, which also
     * covers what happened while the app was closed (that is when the scheduled
     * backup runs). Falls back to the in-memory buffer when the file is not
     * there yet.
     */
    fun exportText(): String {
        val info = buildString {
            appendLine("Osmium log export")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("fingerprint: ${Build.FINGERPRINT}")
        }
        val onDisk = runCatching {
            liveFile?.takeIf { it.exists() }?.readLines()?.takeLast(EXPORT_LINES)
        }.getOrNull()
        val body = if (!onDisk.isNullOrEmpty()) {
            "── last ${onDisk.size} lines (on disk: includes runs while the app was closed) ──\n" +
                onDisk.joinToString("\n")
        } else {
            "── last ${snapshot().size} lines (session only) ──\n" +
                snapshot().joinToString("\n")
        }
        return info + body
    }
}
