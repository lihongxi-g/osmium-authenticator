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

    /**
     * Swatch grid shown in the tag editor: twelve distinct hues at a medium
     * brightness, so they stay readable on the light theme and do not collapse
     * into near-black dots on the dark theme (the earlier set sat around M3
     * tone 40).
     */
    val PALETTE = listOf(
        "#E05450", // red
        "#E8813C", // orange
        "#DFA828", // amber
        "#A9A82B", // olive
        "#57A85A", // green
        "#2FA98C", // teal
        "#2E9FBF", // cyan
        "#3F8BD6", // blue
        "#6E7BDB", // indigo
        "#9B6AD4", // purple
        "#D766A6", // pink
        "#7C8794"  // slate
    )

    private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}")
    private val NAMED_HEX = mapOf(
        RED to "#E05450",
        ORANGE to "#E8813C",
        YELLOW to "#DFA828",
        GREEN to "#57A85A",
        CYAN to "#2E9FBF",
        BLUE to "#3F8BD6",
        PURPLE to "#9B6AD4"
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

    /**
     * The palette swatch that currently represents [value], or null for a
     * custom color. Named presets resolve through [hexFor], so a legacy "blue"
     * tag lights up the matching swatch instead of showing no selection at all.
     */
    fun paletteEntryFor(value: String): String? {
        val hex = hexFor(value)
        return PALETTE.firstOrNull { it.equals(hex, ignoreCase = true) }
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
}

/** Pure account filtering; keeps live TOTP rendering separate from stable filters. */
object TagFilter {
    fun apply(
        accounts: List<Account>,
        query: String,
        selectedTagIds: Set<String>
    ): List<Account> {
        val normalizedQuery = query.trim()
        return accounts.filter { account ->
            val searchMatches = normalizedQuery.isBlank() ||
                account.issuer.contains(normalizedQuery, ignoreCase = true) ||
                account.label.contains(normalizedQuery, ignoreCase = true)
            val tagMatches = selectedTagIds.isEmpty() || account.tags.any { it.id in selectedTagIds }
            searchMatches && tagMatches
        }
    }
}
