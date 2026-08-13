package com.safekey.authenticator.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Copies a code to the clipboard and schedules an automatic clear.
 * The clipboard is only cleared if it still holds the exact code we wrote
 * (never clobbers something the user copied afterwards).
 */
object ClipboardHelper {

    private val handler = Handler(Looper.getMainLooper())
    private var scheduled: Runnable? = null

    fun copy(context: Context, code: String, clearAfterSeconds: Int) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TOTP code", code))

        if (clearAfterSeconds > 0) {
            scheduled?.let { handler.removeCallbacks(it) }
            val runnable = object : Runnable {
                override fun run() {
                    val clip = cm.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(context.applicationContext).toString()
                        if (text == code) {
                            // Android 13+ shows a system toast on clipboard set;
                            // clearing with a placeholder keeps the toast from leaking the code.
                            cm.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    }
                }
            }
            scheduled = runnable
            handler.postDelayed(runnable, clearAfterSeconds * 1000L)
        }
    }
}
