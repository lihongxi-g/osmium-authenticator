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
    object Export : Screen()
    object Import : Screen()
    /** mode: "pin" = app PIN, "destroy_pin" = self-destruct PIN */
    data class PinSetup(val mode: String) : Screen()
    /** Verify the current app PIN (before changing it). */
    object PinVerify : Screen()
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
