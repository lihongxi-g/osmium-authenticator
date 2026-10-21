package com.safekey.authenticator.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.safekey.authenticator.SafeKeyApp
import com.safekey.authenticator.security.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs the scheduled backup when its alarm fires, and re-arms the alarm after
 * reboots, app updates and clock/time-zone changes (an AlarmManager alarm is
 * lost in all of those cases).
 *
 * The backup itself runs here rather than in a WorkManager job: a broadcast
 * that came from an alarm already holds the wake-up, so finishing inside
 * [goAsync] is the shortest path from "the alarm rang" to "the file exists".
 * A local backup takes well under a second; a WebDAV upload is a few kilobytes.
 */
class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isFire = action == AutoBackupAlarm.ACTION_FIRE
        val isRearmOnly = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        if (!isFire && !isRearmOnly) return

        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = appContext as? SafeKeyApp ?: return@launch
                val settings = app.settingsRepository.settings.first()
                if (isFire) {
                    AppLog.d("auto-backup alarm fired")
                    AutoBackupRunner.runScheduled(app)
                } else {
                    AppLog.d("auto-backup re-arm: $action")
                }
                // The next wake-up is always armed by the trigger that just
                // consumed the previous one.
                AutoBackupScheduler.schedule(appContext, settings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.d("auto-backup receiver error: ${e.javaClass.simpleName}: ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }
}
