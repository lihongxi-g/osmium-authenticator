package com.safekey.authenticator.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.data.WebDavServerConfig
import com.safekey.authenticator.integrity.IntegrityEngine
import com.safekey.authenticator.network.LocalNetworkAccess
import com.safekey.authenticator.network.LocalNetworkPermissionException
import com.safekey.authenticator.network.WebDavClient
import com.safekey.authenticator.notify.BackupNotifier
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.PinManager
import com.safekey.authenticator.security.RootState
import com.safekey.authenticator.security.VaultIO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The single code path that produces an automatic backup, shared by the
 * scheduled worker and the "back up now" button so both behave (and report)
 * identically.
 *
 * Every stage is logged through [AppLog] — the previous version logged nothing
 * on success and nothing on most failures, so an exported log could not tell
 * "the worker never ran" apart from "the file could not be written". A backup
 * that fails must never fail silently: [runAndRecord] always writes the
 * outcome (empty error = success) to settings, where the UI shows it.
 *
 * The local write is read back and compared before it counts as success —
 * MediaStore can hand back a URI for a file that never materialised.
 */
object AutoBackupRunner {

    private const val FILE_PREFIX = "osmium-auto-"

    /** Sub-folder of the public Download directory used for local backups. */
    const val LOCAL_FOLDER = "Osmium"

    /** Serialises runs: the catch-up path and a manual tap can overlap. */
    private val lock = Mutex()

    sealed interface Outcome {
        data class Success(
            val fileName: String,
            val accounts: Int,
            val bytes: Int,
            /** Set for the WebDAV target: the server the file was uploaded to. */
            val serverUrl: String? = null,
            val elapsedMs: Long = 0L
        ) : Outcome

        /** Nothing was written; [reason] is the code shown to the user. */
        data class Skipped(val reason: String) : Outcome

        /** The backup started and failed; [reason] carries the exception. */
        data class Failed(val reason: String) : Outcome
    }

    /** Value stored in settings: an empty error means the last run succeeded. */
    fun errorCode(outcome: Outcome): String = when (outcome) {
        is Outcome.Success -> ""
        is Outcome.Skipped -> outcome.reason
        is Outcome.Failed -> outcome.reason
    }

    /**
     * Run one backup and persist the result for the UI. Expected failures come
     * back as [Outcome.Skipped]/[Outcome.Failed]; only cancellation is thrown.
     */
    suspend fun runAndRecord(app: SafeKeyApp, notify: Boolean = false): Outcome = lock.withLock {
        val settings = app.settingsRepository.settings.first()
        // Written before any work starts: an attempt whose process is killed
        // mid-run still leaves a trace in the UI, which is what tells "the
        // scheduler never fired" apart from "the run died half way".
        app.settingsRepository.setAutoBackupAttempt(System.currentTimeMillis())
        val outcome = try {
            run(app, settings)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("auto-backup FAILED: ${e.javaClass.simpleName}: ${e.message}")
            e.stackTrace.take(6).forEach { AppLog.d("  at $it") }
            Outcome.Failed("${e.javaClass.simpleName}: ${e.message}")
        }
        app.settingsRepository.setAutoBackupResult(
            errorCode(outcome), System.currentTimeMillis()
        )
        AppLog.d("auto-backup result: ${errorCode(outcome).ifEmpty { "OK" }}")
        // Unattended runs tell the user through a notification; a manual
        // "back up now" does not (the screen already shows the result, and the
        // user is looking at it).
        if (notify && settings.autoBackupNotify) {
            runCatching { BackupNotifier.notifyOutcome(app, outcome) }
                .onFailure { AppLog.d("backup notification failed: ${it.javaClass.simpleName}") }
        }
        outcome
    }

    /**
     * Entry point for the two scheduled triggers — the WorkManager job and the
     * alarm, which both aim at the same slot and either may fire late. A slot
     * that already succeeded inside the dedupe window is absorbed silently:
     * no second file, no overwritten status. A failed run is retried.
     */
    suspend fun runScheduled(app: SafeKeyApp): Outcome {
        val settings = app.settingsRepository.settings.first()
        if (AutoBackupScheduler.isRecentSuccess(
                System.currentTimeMillis(),
                settings.autoBackupLastTime,
                settings.autoBackupLastError
            )
        ) {
            AppLog.d("auto-backup skipped: RECENT_SUCCESS (other trigger already ran)")
            return Outcome.Skipped("RECENT_SUCCESS")
        }
        return runAndRecord(app, notify = true)
    }

    suspend fun run(app: SafeKeyApp, settings: AppSettings): Outcome {
        val started = System.currentTimeMillis()
        val local = settings.autoBackupTarget == AppSettings.AUTO_BACKUP_TARGET_LOCAL
        AppLog.d(
            "auto-backup start: target=${if (local) "local" else "webdav"} " +
                "keep=${settings.autoBackupKeepCount}"
        )

        // Root hardening: the gate exists so the vault does not *leave* the
        // device while the root restriction is active. Two corrections to how
        // it used to work (2.5.3–2.5.8), both of which turned every unattended
        // run into a silent skip:
        //  • a local backup never leaves the device — it is an encrypted file in
        //    this phone's Download folder — so an integrity verdict must not
        //    block it, and the expensive scan is not run for it at all;
        //  • a scan that produced no verdict (INTEGRITY_UNKNOWN) is not
        //    evidence of a compromised device, only a missing measurement. For
        //    the local target that state now simply proceeds; for a target that
        //    does leave the device it still fails closed.
        if (!settings.devDisableRootSecurity) {
            val report = if (local) {
                RootState.report.value
            } else {
                RootState.report.value
                    ?: runCatching { IntegrityEngine.scan(app) }.getOrNull()
            }
            val blocked = if (local) report?.compromised == true else report?.compromised != false
            if (blocked) {
                val reason = if (report == null) "INTEGRITY_UNKNOWN" else "ROOT_RESTRICTED"
                AppLog.d("auto-backup skipped: $reason (integrity verdict unavailable or compromised)")
                return Outcome.Skipped(reason)
            }
        }

        val password = app.settingsRepository.getAutoBackupPassword()
        if (password == null) {
            AppLog.d("auto-backup skipped: NO_PASSWORD")
            return Outcome.Skipped("NO_PASSWORD")
        }

        val pin = PinManager(app).getPinHashForExport()
        val export = app.accountRepository.exportVault(
            pinSalt = pin?.first ?: "",
            pinHash = pin?.second ?: ""
        )
        // A backup that silently omits accounts is worse than no backup: abort
        // (and skip the pruning below) instead of letting an incomplete file
        // overwrite the good ones kept by the retention rule.
        if (export.dropped > 0) {
            AppLog.d("auto-backup skipped: EXPORT_INCOMPLETE:${export.dropped}")
            return Outcome.Skipped("EXPORT_INCOMPLETE:${export.dropped}")
        }

        val accounts = export.vault.accounts.size
        val payload = VaultIO.encrypt(export.vault, password.toCharArray())
            .toByteArray(Charsets.UTF_8)
        val fileName = "$FILE_PREFIX${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        }.json"
        AppLog.d("auto-backup payload: accounts=$accounts bytes=${payload.size} file=$fileName")

        if (local) {
            val uri = writeLocalBackup(app, fileName, payload)
            AppLog.d("auto-backup local write ok: $uri")
            val pruned = pruneLocalBackups(app, settings.autoBackupKeepCount)
            if (pruned > 0) AppLog.d("auto-backup prune: deleted $pruned local file(s)")
            return Outcome.Success(
                fileName = fileName,
                accounts = accounts,
                bytes = payload.size,
                elapsedMs = System.currentTimeMillis() - started
            )
        }

        val config = app.settingsRepository.webDavConfig.first()
        if (config == null || config.baseUrl.isBlank()) {
            AppLog.d("auto-backup skipped: NO_SERVER")
            return Outcome.Skipped("NO_SERVER")
        }
        // A worker/foreground run cannot request runtime permissions, so a
        // denial is reported instead of prompting (the UI offers the switch).
        try {
            LocalNetworkAccess.checkWebDav(app, config.baseUrl)
        } catch (_: LocalNetworkPermissionException) {
            AppLog.d("auto-backup skipped: LOCAL_NETWORK_PERMISSION")
            return Outcome.Skipped("LOCAL_NETWORK_PERMISSION")
        }
        WebDavClient.upload(config, fileName, payload)
        AppLog.d("auto-backup webdav upload ok: ${config.baseUrl} $fileName")
        val pruned = pruneWebDavBackups(config, settings.autoBackupKeepCount)
        if (pruned > 0) AppLog.d("auto-backup prune: deleted $pruned remote file(s)")
        return Outcome.Success(
            fileName = fileName,
            accounts = accounts,
            bytes = payload.size,
            serverUrl = config.baseUrl,
            elapsedMs = System.currentTimeMillis() - started
        )
    }

    /**
     * Prune auto-backups beyond [keep]. [WebDavClient.listBackups] returns
     * files sorted by name (timestamped names → chronological) — trust that
     * order instead of re-sorting by lastModified, which some servers leave
     * unset (0), scrambling the order and deleting the wrong files.
     */
    private fun pruneWebDavBackups(config: WebDavServerConfig, keep: Int): Int {
        val old = WebDavClient.listBackups(config)
            .filter { it.name.startsWith(FILE_PREFIX) }
            .drop(keep)
        old.forEach { WebDavClient.delete(config, it.href) }
        return old.size
    }

    // -------------------------------------------------------- local storage

    /**
     * Write the payload into the public Download/Osmium folder and return the
     * resulting document URI. The content is read back and length-compared:
     * an insert that produced a row but no bytes must fail loudly.
     */
    private fun writeLocalBackup(context: Context, fileName: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + LOCAL_FOLDER
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IOException("MediaStore insert returned no uri")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IOException("MediaStore openOutputStream returned null")
                val publish = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                if (resolver.update(uri, publish, null, null) <= 0) {
                    throw IOException("MediaStore publish failed (no row updated)")
                }
                val readBack = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("MediaStore read-back returned null")
                if (readBack.size != bytes.size) {
                    throw IOException(
                        "Size mismatch after write: wrote ${bytes.size}, read ${readBack.size}"
                    )
                }
                return uri.toString()
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            LOCAL_FOLDER
        )
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Cannot create ${dir.absolutePath}")
        }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        if (file.length() != bytes.size.toLong()) {
            throw IOException(
                "Size mismatch after write: wrote ${bytes.size}, on disk ${file.length()}"
            )
        }
        return file.absolutePath
    }

    private fun pruneLocalBackups(context: Context, keep: Int): Int {
        var deleted = 0
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? " +
                "AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf(
                Environment.DIRECTORY_DOWNLOADS + "/" + LOCAL_FOLDER + "/",
                "$FILE_PREFIX%"
            )
            // Timestamped file names sort chronologically — deterministic
            // even if DATE_MODIFIED were missing on some provider rows.
            val sortOrder = "${MediaStore.Downloads.DISPLAY_NAME} DESC"
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, sortOrder
            )?.use { cursor ->
                var index = 0
                while (cursor.moveToNext()) {
                    if (index >= keep) {
                        val id = cursor.getLong(0)
                        deleted += resolver.delete(
                            ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                            ), null, null
                        )
                    }
                    index++
                }
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                LOCAL_FOLDER
            )
            if (!dir.isDirectory) return 0
            dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) }
                ?.sortedByDescending { it.name }
                ?.drop(keep)
                ?.forEach { if (it.delete()) deleted++ }
        }
        return deleted
    }
}
