package com.safekey.authenticator.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Copies a code to the clipboard and schedules an automatic clear.
 *
 * The clear is scheduled as a WorkManager one-time job (survives process
 * death; a Handler alone would die with the app). Because Android 10+
 * forbids clipboard reads from background apps, the worker clears
 * unconditionally when it cannot read — see ClipboardClearWorker.
 */
object ClipboardHelper {

    private const val UNIQUE_WORK = "clipboard_clear"

    fun copy(context: Context, code: String, clearAfterSeconds: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TOTP code", code))

        if (clearAfterSeconds > 0) {
            val request = OneTimeWorkRequestBuilder<ClipboardClearWorker>()
                .setInitialDelay(clearAfterSeconds.toLong(), TimeUnit.SECONDS)
                .setInputData(workDataOf(ClipboardClearWorker.KEY_EXPECTED to code))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
