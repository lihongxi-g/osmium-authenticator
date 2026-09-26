package com.safekey.authenticator.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.safekey.authenticator.totp.ParsedOtpUri

/** All screens in the app. State lives in the ViewModel so rotation keeps the stack. */
sealed class Screen {
    object Accounts : Screen()
    data class AccountForm(
        val accountId: String? = null,
        val prefill: ParsedOtpUri? = null
    ) : Screen()
    data class Detail(val accountId: String) : Screen()
    object Scan : Screen()
    object Settings : Screen()
    object Appearance : Screen()
    object TagSettings : Screen()
    object Export : Screen()
    object Import : Screen()
    /** WebDAV backup to a user-configured server on the local network. */
    object WebDav : Screen()
    /** Scheduled automatic backup (WebDAV server or local storage). */
    object AutoBackup : Screen()
    /** Quick LAN transfer between nearby devices over Wi-Fi. */
    object LanTransfer : Screen()
    /** mode: "pin" = app PIN, "destroy_pin" = self-destruct PIN */
    data class PinSetup(val mode: String) : Screen()
    /** Verify the current app PIN before a sensitive action.
     * next: change_pin | clear_pin | set_destroy_pin | change_destroy_pin
     */
    data class PinVerify(val next: String) : Screen()
    /** Share a single account as an otpauth:// QR code. */
    data class ShareQr(val accountId: String) : Screen()
    object About : Screen()
    object Manual : Screen()
    object SortOrder : Screen()
    object GoogleImport : Screen()
    /** File import from other authenticators (Aegis/2FAS/Raivo export files). */
    object FileImport : Screen()
    /** Source picker for third-party authenticator migration. */
    object ThirdPartyImport : Screen()
    /** Open-source attributions and format-source references. */
    object Attributions : Screen()
    object Tags : Screen()
    /** Android device integrity report (K1 local checks + K3 key attestation). */
    object Integrity : Screen()
    /** Hidden developer-mode panel (English / Simplified Chinese only). */
    object Developer : Screen()
}

/**
 * Lightweight navigation stack with transition direction tracking.
 * SnapshotStateList keeps Compose recomposition working; ViewModel hosting
 * keeps it alive across configuration changes.
 */
class NavigationState(initial: Screen = Screen.Accounts) {
    private val stack = mutableStateListOf(initial)
    var direction by mutableStateOf(1) // +1 push, -1 pop
        private set

    val current: Screen get() = stack.last()
    /** The screen below the top one — the target a back gesture reveals. */
    val previous: Screen? get() = if (stack.size > 1) stack[stack.size - 2] else null
    val canGoBack: Boolean get() = stack.size > 1
    val size: Int get() = stack.size

    fun push(screen: Screen) {
        direction = 1
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        direction = -1
        stack.removeAt(stack.lastIndex)
        return true
    }

    /** Pop until only the root remains. */
    fun popToRoot() {
        if (stack.size <= 1) return
        direction = -1
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

/** Screens that are unavailable while the root security restriction is active. */
fun Screen.isRootBlocked(): Boolean = when (this) {
    is Screen.WebDav, is Screen.AutoBackup, is Screen.LanTransfer,
    is Screen.ThirdPartyImport, is Screen.GoogleImport, is Screen.FileImport -> true
    else -> false
}
