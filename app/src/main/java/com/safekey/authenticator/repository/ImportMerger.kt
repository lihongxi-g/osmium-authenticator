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
        var duplicates = 0

        // Every incoming entry is planned. Grouping by issuer+label and keeping
        // only the first of each group silently dropped the rest: a backup with
        // two accounts sharing issuer and label (e.g. two blank-labelled
        // entries) restored one of them and reported a normal import.
        val claimed = HashSet<String>()
        for (entry in incoming) {
            val key = keyOf(entry.issuer, entry.label)
            val match = existing.firstOrNull {
                keyOf(it.issuer, it.label) == key && it.id !in claimed
            }
            if (match != null) {
                claimed += match.id
                toUpdate.add(match to entry.withSafeHotpCounter(match.counter))
                duplicates += 1
            } else {
                toAdd.add(entry)
            }
        }
        return ImportPlan(toAdd, toUpdate, duplicates)
    }

    /**
     * Restoring an older backup must never rewind an HOTP counter: the server
     * rejects codes below the counter it already accepted. Keep whichever
     * counter is higher. (TOTP counters are unused and left untouched.)
     */
    private fun VaultAccount.withSafeHotpCounter(localCounter: Long): VaultAccount =
        if (type == Account.TYPE_HOTP) copy(counter = maxOf(counter, localCounter)) else this

    private fun keyOf(issuer: String, label: String): String =
        "${issuer.trim().lowercase()}|${label.trim().lowercase()}"
}
