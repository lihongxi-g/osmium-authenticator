package com.safekey.authenticator.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.security.PinManager
import com.safekey.authenticator.security.VaultIO
import com.safekey.authenticator.network.WebDavClient
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unattended backup: builds the vault, encrypts it with the stored export
 * password (PBKDF2 + AES-256-GCM, same portable format as manual backups),
 * and uploads it to the configured WebDAV server or writes it to the public
 * Download/Osmium folder. Old auto-backups are pruned (newest 5 kept).
 *
 * The result (error string + timestamp) is stored in settings and shown on
 * the auto-backup screen; nothing pops up over the user. On completion the
 * next run is re-scheduled so the time-of-day stays accurate.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val KEEP_BACKUPS = 5
        private const val FILE_PREFIX = "osmium-auto-"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as SafeKeyApp
        val settingsRepo = app.settingsRepository
        val settings = settingsRepo.settings.first()
        if (!settings.autoBackupEnabled) {
            AutoBackupScheduler.schedule(applicationContext, settings)
            return Result.success()
        }
        try {
            runBackup(app, settings)
        } catch (e: Exception) {
            settingsRepo.setAutoBackupResult(
                "${e.javaClass.simpleName}: ${e.message}", System.currentTimeMillis()
            )
            // One retry for transient failures (network blips), then give up —
            // the next scheduled run will try again.
            return if (runAttemptCount < 1) Result.retry() else Result.success()
        } finally {
            val latest = settingsRepo.settings.first()
            AutoBackupScheduler.schedule(applicationContext, latest)
        }
        return Result.success()
    }

    private suspend fun runBackup(app: SafeKeyApp, settings: AppSettings) {
        val password = app.settingsRepository.getAutoBackupPassword()
        if (password == null) {
            app.settingsRepository.setAutoBackupResult("NO_PASSWORD", System.currentTimeMillis())
            return
        }
        val pin = PinManager(applicationContext).getPinHashForExport()
        val vault = app.accountRepository.exportVault(
            pinSalt = pin?.first ?: "",
            pinHash = pin?.second ?: ""
        )
        val payload = VaultIO.encrypt(vault, password.toCharArray())
            .toByteArray(Charsets.UTF_8)
        val fileName = "$FILE_PREFIX${
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        }.json"

        when (settings.autoBackupTarget) {
            AppSettings.AUTO_BACKUP_TARGET_LOCAL -> {
                writeLocalBackup(applicationContext, fileName, payload)
                pruneLocalBackups(applicationContext)
            }
            else -> {
                val config = app.settingsRepository.webDavConfig.first()
                if (config == null || config.baseUrl.isBlank()) {
                    app.settingsRepository.setAutoBackupResult(
                        "NO_SERVER", System.currentTimeMillis()
                    )
                    return
                }
                WebDavClient.upload(config, fileName, payload)
                pruneWebDavBackups(config)
            }
        }
        app.settingsRepository.setAutoBackupResult("", System.currentTimeMillis())
    }

    private fun pruneWebDavBackups(config: com.safekey.authenticator.data.WebDavServerConfig) {
        val old = WebDavClient.listBackups(config)
            .filter { it.name.startsWith(FILE_PREFIX) }
            .sortedByDescending { it.lastModified }
            .drop(KEEP_BACKUPS)
        old.forEach { WebDavClient.delete(config, it.href) }
    }

    // -------------------------------------------------------- local storage

    private fun writeLocalBackup(context: Context, fileName: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Osmium")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert failed")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IOException("MediaStore open failed")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Osmium"
            )
            if (!dir.exists() && !dir.mkdirs()) {
                throw IOException("Cannot create ${dir.absolutePath}")
            }
            File(dir, fileName).writeBytes(bytes)
        }
    }

    private fun pruneLocalBackups(context: Context) {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME
            )
            val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? " +
                "AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val args = arrayOf(
                Environment.DIRECTORY_DOWNLOADS + "/Osmium/",
                "$FILE_PREFIX%"
            )
            val sortOrder = "${MediaStore.Downloads.DATE_MODIFIED} DESC"
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, sortOrder
            )?.use { cursor ->
                var index = 0
                while (cursor.moveToNext()) {
                    if (index >= KEEP_BACKUPS) {
                        val id = cursor.getLong(0)
                        resolver.delete(
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
                "Osmium"
            )
            if (!dir.isDirectory) return
            dir.listFiles { f -> f.name.startsWith(FILE_PREFIX) }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(KEEP_BACKUPS)
                ?.forEach { it.delete() }
        }
    }
}
