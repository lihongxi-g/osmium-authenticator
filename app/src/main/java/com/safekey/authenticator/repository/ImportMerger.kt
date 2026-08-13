package com.safekey.authenticator.repository

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount

/** Result of merging an import into the existing account set. */
data class ImportPlan(
    val toAdd: List<VaultAccount>,
    val toUpdate: List<Pair<Account, VaultAccount>>,
    val duplicatesCount: Int
) {
    val total: Int get() = toAdd.size + toUpdate.size
}

/**
 * Pure merge logic (no Android dependencies) — unit-testable on the JVM.
 * Matching rule: same issuer AND same label (case-insensitive).
 */
object ImportMerger {

    fun plan(existing: List<Account>, incoming: List<VaultAccount>): ImportPlan {
        val toAdd = mutableListOf<VaultAccount>()
        val toUpdate = mutableListOf<Pair<Account, VaultAccount>>()
        val incomingByKey = incoming.groupBy { keyOf(it.issuer, it.label) }
        var duplicates = 0

        for ((key, items) in incomingByKey) {
            val first = items.first()
            val match = existing.firstOrNull { keyOf(it.issuer, it.label) == key }
            if (match != null) {
                toUpdate.add(match to first)
                duplicates += 1
            } else {
                toAdd.add(first)
            }
        }
        return ImportPlan(toAdd, toUpdate, duplicates)
    }

    private fun keyOf(issuer: String, label: String): String =
        "${issuer.trim().lowercase()}|${label.trim().lowercase()}"
}
