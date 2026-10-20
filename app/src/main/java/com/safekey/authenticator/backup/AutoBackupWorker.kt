package com.safekey.authenticator.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.security.AppLog
import kotlinx.coroutines.flow.first

/**
 * Unattended backup: delegates to [AutoBackupRunner], which builds the vault,
 * encrypts it with the stored export password (PBKDF2 + AES-256-GCM, same
 * portable format as manual backups) and uploads it to the configured WebDAV
 * server or writes it to the public Download/Osmium folder, pruning older
 * auto-backups (newest N kept).
 *
 * The result (error string + timestamp) is stored in settings and shown on the
 * auto-backup screen; nothing pops up over the user. On completion the next run
 * is re-scheduled so the time-of-day stays accurate.
 */
class AutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SafeKeyApp
        val settingsRepo = app.settingsRepository
        val settings = settingsRepo.settings.first()
        if (!settings.autoBackupEnabled) {
            AutoBackupScheduler.schedule(applicationContext, settings)
            return Result.success()
        }
        // Logged before anything else: an exported log must show whether the
        // scheduled run was started at all (the old worker logged nothing).
        AppLog.d("auto-backup worker: attempt=$runAttemptCount")
        return try {
            val outcome = AutoBackupRunner.runAndRecord(app)
            // One retry for transient failures (network blips), then give up —
            // the next scheduled run will try again.
            if (outcome is AutoBackupRunner.Outcome.Failed && runAttemptCount < 1) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            AppLog.d("auto-backup worker: cancelled")
            throw e
        } finally {
            val latest = runCatching { settingsRepo.settings.first() }.getOrDefault(settings)
            AutoBackupScheduler.schedule(applicationContext, latest)
        }
    }
}
