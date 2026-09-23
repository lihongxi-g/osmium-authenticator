package com.safekey.authenticator.security

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * In-memory ring buffer (last ~100 lines) + crash persistence.
 * Export copies the latest 100 lines plus device info to the clipboard —
 * no permissions, no network, nothing leaves the device unless the user
 * pastes it somewhere.
 */
object AppLog {

    private const val MAX_LINES = 100
    private val buffer = ArrayDeque<String>(MAX_LINES + 8)
    private val lock = Any()
    private var logFile: File? = null

    fun init(context: Context) {
        logFile = File(context.filesDir, "safekey-log.txt")
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

    fun exportText(): String {
        val info = buildString {
            appendLine("Osmium log export")
            appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("fingerprint: ${Build.FINGERPRINT}")
            appendLine("── last ${MAX_LINES} lines ──")
        }
        return info + snapshot().joinToString("\n")
    }
}
