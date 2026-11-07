package com.safekey.authenticator.tags

/**
 * Pure selection maths for the "which accounts belong to this tag" screen.
 *
 * That screen is a full picture of one tag's membership: whatever stays ticked is
 * linked, whatever is unticked is unlinked. Keeping the rules here means they can
 * be unit-tested without Room or Compose.
 */
object TagBatch {

    /** Every (accountId, tagId) link to write, in a stable order. */
    fun refs(tagId: String, accountIds: Collection<String>): List<Pair<String, String>> {
        if (tagId.isEmpty()) return emptyList()
        return accountIds.sorted().map { accountId -> accountId to tagId }
    }

    /** Add or remove one account from the selection. */
    fun toggle(selected: Set<String>, id: String): Set<String> =
        selected.toMutableSet().also { if (!it.add(id)) it.remove(id) }

    /**
     * Select-all / clear-all toggle: selects every account when the selection is
     * incomplete, clears it when everything is already selected.
     */
    fun toggleAll(selected: Set<String>, allIds: Collection<String>): Set<String> {
        val all = allIds.toSet()
        return if (all.isNotEmpty() && selected.containsAll(all)) emptySet() else all
    }

    /** Drop ids that no longer exist (deleted accounts), so the count stays honest. */
    fun retainExisting(selected: Set<String>, allIds: Collection<String>): Set<String> {
        val existing = allIds.toSet()
        return selected.filter { it in existing }.toSet()
    }
}
