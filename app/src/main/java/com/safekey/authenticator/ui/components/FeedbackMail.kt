package com.safekey.authenticator.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.safekey.authenticator.BuildConfig
import com.safekey.authenticator.MainViewModel
import com.safekey.authenticator.R
import com.safekey.authenticator.security.AppLog
import com.safekey.authenticator.security.ClipboardHelper

/**
 * Opens the mail app with the recent log pre-filled, and copies the FULL log to
 * the clipboard as a fallback — a `mailto:` URI has a practical length limit, so
 * only the tail travels in the body.
 *
 * Shared by the About screen and the "leave a few words" button of the
 * migration farewell dialog. (AboutScreen still carries its own private copy;
 * both point at the same support address.)
 */
internal fun sendFeedbackWithLog(context: Context, vm: MainViewModel) {
    val log = AppLog.exportText()
    val tail = log.lines().takeLast(40).joinToString("\n")
    val subject = Uri.encode("Osmium v${BuildConfig.VERSION_NAME} Feedback")
    val body = Uri.encode(tail)
    val uri = "mailto:zhif0776@hotmail.com?subject=$subject&body=$body"
    try {
        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse(uri)))
        ClipboardHelper.copy(context, log)
        vm.showToast(context.getString(R.string.email_log_copied))
    } catch (_: Exception) {
        vm.showToast(context.getString(R.string.no_email_app))
    }
}
