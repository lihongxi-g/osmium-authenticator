package com.safekey.authenticator.tags

/**
 * Pure selection/target maths for the batch tag editor.
 *
 * Kept out of the UI and the repository so the "which accounts get which tags"
 * rules can be unit-tested without Room or Compose: the UI only renders the
 * selection, and the repository only writes what [refs] returns.
 */
object TagBatch {

    /** True when there is something to write (both sides non-empty). */
    fun canApply(accountIds: Collection<String>, tagIds: Collection<String>): Boolean =
        accountIds.isNotEmpty() && tagIds.isNotEmpty()

    /**
     * Every (account, tag) pair to link, in a stable order. The cross-ref table
     * has a composite primary key, so re-adding a tag an account already has is
     * a harmless no-op when written with `OnConflictStrategy.IGNORE`.
     */
    fun refs(accountIds: Collection<String>, tagIds: Collection<String>): List<Pair<String, String>> {
        if (!canApply(accountIds, tagIds)) return emptyList()
        return accountIds.sorted().flatMap { accountId -> tagIds.sorted().map { tagId -> accountId to tagId } }
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

    /** Drop ids that no longer exist (deleted or filtered-away accounts). */
    fun retainExisting(selected: Set<String>, allIds: Collection<String>): Set<String> {
        val existing = allIds.toSet()
        return selected.filter { it in existing }.toSet()
    }
}
