package com.safekey.authenticator.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.safekey.authenticator.MainActivity
import com.safekey.authenticator.R
import com.safekey.authenticator.backup.AutoBackupRunner
import com.safekey.authenticator.security.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Result notifications for the automatic backup.
 *
 * Two channels on purpose (a channel's importance can only be changed by the
 * user once it exists): success is silent and low-priority so a 03:00 backup
 * cannot wake anyone up, failure is normal priority because a backup that did
 * not happen is something the user has to see.
 *
 * This is an authenticator, so the lock screen only ever gets the generic
 * [R.string.notify_backup_private] line: the real content (time, destination,
 * reason) is behind [NotificationCompat.VISIBILITY_PRIVATE]. No account
 * counts or secrets are ever put in a notification.
 */
object BackupNotifier {

    const val CHANNEL_SUCCESS = "osmium_backup_success"
    const val CHANNEL_FAILURE = "osmium_backup_failure"

    /** Understood by MainActivity: which screen to open after unlocking. */
    const val EXTRA_OPEN_SCREEN = "com.safekey.authenticator.extra.OPEN_SCREEN"
    const val SCREEN_AUTO_BACKUP = "auto_backup"

    private const val ID_SUCCESS = 4711
    private const val ID_FAILURE = 4712
    private const val REQUEST_CONTENT = 4713

    /** Created on every app start: cheap, idempotent, survives channel edits. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val success = NotificationChannel(
            CHANNEL_SUCCESS,
            context.getString(R.string.notify_backup_success_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notify_channel_success_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        val failure = NotificationChannel(
            CHANNEL_FAILURE,
            context.getString(R.string.notify_backup_failed_title),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notify_channel_failure_desc)
        }
        manager.createNotificationChannels(listOf(success, failure))
    }

    /** True when the system will actually show our notifications. */
    fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Post the outcome of one unattended run. [AutoBackupRunner.Outcome.Skipped]
     * with [AutoBackupRunner.Outcome.Skipped.reason] "RECENT_SUCCESS" is an
     * internal duplicate-trigger signal and stays silent.
     */
    fun notifyOutcome(context: Context, outcome: AutoBackupRunner.Outcome) {
        if (outcome is AutoBackupRunner.Outcome.Skipped && outcome.reason == "RECENT_SUCCESS") return
        if (!canNotify(context)) {
            AppLog.d("backup notification skipped: notifications disabled")
            return
        }
        ensureChannels(context)

        val success = outcome is AutoBackupRunner.Outcome.Success
        val channel = if (success) CHANNEL_SUCCESS else CHANNEL_FAILURE
        val title = if (success) {
            context.getString(R.string.notify_backup_success_title)
        } else if (outcome is AutoBackupRunner.Outcome.Skipped) {
            context.getString(R.string.notify_backup_skipped_title)
        } else {
            context.getString(R.string.notify_backup_failed_title)
        }
        val text = when (outcome) {
            is AutoBackupRunner.Outcome.Success ->
                if (outcome.serverUrl == null) {
                    context.getString(R.string.auto_backup_saved_local, outcome.fileName)
                } else {
                    context.getString(
                        R.string.auto_backup_saved_webdav, outcome.serverUrl, outcome.fileName
                    )
                }
            is AutoBackupRunner.Outcome.Skipped -> reasonText(context, outcome.reason)
            is AutoBackupRunner.Outcome.Failed -> outcome.reason
        }
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        // Lock-screen variant: no version, no destination, no reason.
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_osmium)
            .setContentTitle(context.getString(R.string.notify_backup_private))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_osmium)
            .setContentTitle("$title · $now")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(
                if (success) NotificationCompat.PRIORITY_LOW
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        val id = if (success) ID_SUCCESS else ID_FAILURE
        NotificationManagerCompat.from(context).notify(id, notification)
        AppLog.d("backup notification posted: ${if (success) "success" else "failure/skip"}")
    }

    /**
     * Tap target: the auto-backup page. MainActivity honours the extra only
     * after its own gate (biometric / PIN) has been passed, so a notification
     * can never be used to skip authentication.
     */
    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_CONTENT,
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .putExtra(EXTRA_OPEN_SCREEN, SCREEN_AUTO_BACKUP)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Localised label for a runner skip/error code. */
    fun reasonText(context: Context, reason: String): String = when {
        reason == "NO_PASSWORD" -> context.getString(R.string.auto_backup_need_password)
        reason == "NO_SERVER" -> context.getString(R.string.auto_backup_webdav_not_configured)
        reason == "LOCAL_NETWORK_PERMISSION" ->
            context.getString(R.string.local_network_permission_desc)
        reason.startsWith("EXPORT_INCOMPLETE") -> reason
        else -> reason
    }

    /** Deep link to the app's notification settings (used by the UI). */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
