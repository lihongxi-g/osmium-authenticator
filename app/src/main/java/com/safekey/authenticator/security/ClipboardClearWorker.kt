package com.safekey.authenticator.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Clears the clipboard after the configured delay.
 *
 * Runs via WorkManager so it fires even after the app process is killed.
 * Android 10+ blocks clipboard READS from background apps, so `current` is
 * null when this executes in the background — in that case we clear
 * unconditionally: a leftover TOTP code is worse than clearing a fresh copy
 * made in the last few seconds. When we CAN read (foreground), we only clear
 * if the clipboard still holds the exact code we put there.
 */
class ClipboardClearWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val expected = inputData.getString(KEY_EXPECTED)
        val cm = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val current = try {
            cm.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(applicationContext)
                ?.toString()
        } catch (_: Exception) {
            null
        }
        if (expected != null && (current == null || current == expected)) {
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
            AppLog.d("clipboard cleared (current=${if (current == null) "unreadable" else "match"})")
        } else {
            AppLog.d("clipboard kept (content changed)")
        }
        return Result.success()
    }

    companion object {
        const val KEY_EXPECTED = "expected"
    }
}
