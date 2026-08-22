package com.safekey.authenticator.tags

import com.safekey.authenticator.model.Account

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
