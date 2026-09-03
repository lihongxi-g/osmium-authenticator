package com.safekey.authenticator.tags

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.Tag

/** Preset tag color keys. Compose resolves them through the active M3 theme. */
object TagColor {
    const val RED = "red"
    const val ORANGE = "orange"
    const val YELLOW = "yellow"
    const val GREEN = "green"
    const val CYAN = "cyan"
    const val BLUE = "blue"
    const val PURPLE = "purple"

    val ALL = listOf(RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PURPLE)

    /** A compact palette of muted colors for quick visual selection. */
    val PALETTE = listOf(
        "#536A8A", "#65758B", "#756B8E", "#8B687A",
        "#8A6B58", "#7B7455", "#5F7C70", "#4E7D84",
        "#657F9A", "#8A6A4E", "#79617D", "#5D7668"
    )

    private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    private val NAMED_HEX = mapOf(
        RED to "#BA1A1A",
        ORANGE to "#9A4500",
        YELLOW to "#806000",
        GREEN to "#006D3B",
        CYAN to "#006874",
        BLUE to "#45618F",
        PURPLE to "#6750A4"
    )

    fun isValid(value: String): Boolean = value in ALL || HEX_COLOR.matches(value.trim())

    fun normalize(value: String): String {
        val clean = value.trim()
        return when {
            clean in ALL -> clean
            HEX_COLOR.matches(clean) -> clean.uppercase()
            else -> BLUE
        }
    }

    fun hexFor(value: String): String = when {
        value in ALL -> NAMED_HEX.getValue(value)
        HEX_COLOR.matches(value.trim()) -> value.trim().uppercase()
        else -> NAMED_HEX.getValue(BLUE)
    }

    fun toComposeColor(value: String): androidx.compose.ui.graphics.Color =
        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hexFor(value)))
}

/** Pure tag feature presentation rules shared by UI and tests. */
object TagPresentation {
    fun visibleTags(enabled: Boolean, tags: List<Tag>): List<Tag> =
        if (enabled) tags else emptyList()

    fun shouldShowFilterRow(enabled: Boolean, tags: List<Tag>): Boolean =
        enabled && tags.isNotEmpty()

    fun shouldShowUncategorized(enabled: Boolean, tags: List<Tag>): Boolean =
        shouldShowFilterRow(enabled, tags)
}

/** Pure account filtering; keeps live TOTP rendering separate from stable filters. */
object TagFilter {
    fun apply(
        accounts: List<Account>,
        query: String,
        selectedTagIds: Set<String>,
        showUncategorized: Boolean
    ): List<Account> {
        val normalizedQuery = query.trim()
        return accounts.filter { account ->
            val searchMatches = normalizedQuery.isBlank() ||
                account.issuer.contains(normalizedQuery, ignoreCase = true) ||
                account.label.contains(normalizedQuery, ignoreCase = true)
            val tagMatches = when {
                showUncategorized -> account.tags.isEmpty()
                selectedTagIds.isEmpty() -> true
                else -> account.tags.any { it.id in selectedTagIds }
            }
            searchMatches && tagMatches
        }
    }
}
