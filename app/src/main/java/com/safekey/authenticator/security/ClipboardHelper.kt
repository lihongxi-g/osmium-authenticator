package com.safekey.authenticator.security

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Plain clipboard copy — no auto-clear (removed per user request).
 */
object ClipboardHelper {

    fun copy(context: Context, code: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("TOTP code", code))
    }
}
