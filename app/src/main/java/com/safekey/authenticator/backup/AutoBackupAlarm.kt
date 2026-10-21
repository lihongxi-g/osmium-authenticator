package com.safekey.authenticator.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.safekey.authenticator.data.AppSettings
import com.safekey.authenticator.security.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wall-clock wake-up for the scheduled backup.
 *
 * WorkManager hands the delay to JobScheduler, and on phones that sleep all
 * night (Doze plus OEM "sleep standby" / app freeze) that job is routinely
 * deferred for hours or never runs — the backup silently never happens while
 * "Back up now" in the foreground works fine. An `AlarmManager`
 * `setAndAllowWhileIdle(RTC_WAKEUP)` alarm is delivered by the system even in
 * Doze, so the backup still fires around the configured time.
 *
 * Inexact on purpose: `setExactAndAllowWhileIdle` needs the SCHEDULE_EXACT_ALARM
 * special permission (denied by default on Android 13+), and a nightly backup
 * does not need second-level precision. The system may move the alarm by a few
 * minutes; it is delivered from a low-power state, which is the part that
 * matters.
 *
 * The alarm does not survive a reboot or a time-zone change — [AutoBackupScheduler]
 * re-arms it from the app, and [AutoBackupReceiver] re-arms it on
 * BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED.
 */
object AutoBackupAlarm {

    const val ACTION_FIRE = "com.safekey.authenticator.action.AUTO_BACKUP_FIRE"

    private const val REQUEST_CODE = 4113

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AutoBackupReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Arm (or re-arm) the next wake-up. A pending alarm is replaced. */
    fun schedule(context: Context, settings: AppSettings) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        if (!settings.autoBackupEnabled) {
            manager.cancel(pendingIntent(context))
            return
        }
        val now = System.currentTimeMillis()
        val next = AutoBackupScheduler.nextRunMillis(
            now = now,
            hour = settings.autoBackupHour,
            minute = settings.autoBackupMinute,
            intervalDays = settings.autoBackupIntervalDays
        )
        runCatching {
            manager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, next, pendingIntent(context)
            )
        }.onSuccess {
            val at = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(next))
            AppLog.d("auto-backup alarm armed for $at")
        }.onFailure {
            AppLog.d("auto-backup alarm failed: ${it.javaClass.simpleName}: ${it.message}")
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }
}
