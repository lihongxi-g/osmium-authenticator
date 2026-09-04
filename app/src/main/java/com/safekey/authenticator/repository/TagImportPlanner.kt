package com.safekey.authenticator.repository

import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.model.VaultTag
import com.safekey.authenticator.tags.TagRules
import com.safekey.authenticator.tags.TagColor

/**
 * Pure planning for the tag half of a vault import (same pattern as
 * [ImportMerger]) so the merge rules are unit-testable.
 *
 * Rules:
 *  - local tags are matched case-insensitively on the trimmed name; a match
 *    reuses the local tag id and never overwrites its color
 *  - invalid incoming names (blank, or longer than [TagRules.MAX_NAME_LENGTH])
 *    are skipped — a malformed backup must never crash the import
 *  - duplicate names inside the backup collapse onto the first occurrence
 *
 * The caller creates [Plan.toCreate] tags in the database, then replaces their
 * placeholder ids (the incoming ids) with the real local ids; entries that
 * stay unresolved are dropped so account refs can never violate the tags
 * foreign key. See MainViewModel.prepareImport.
 */
object TagImportPlanner {

    data class Plan(
        /** Incoming tag id → local tag id (placeholder ids for [toCreate]). */
        val idMap: Map<String, String>,
        /** Tags that need to be created locally (deduplicated, valid names). */
        val toCreate: List<VaultTag>
    )

    fun plan(existing: List<Tag>, incoming: List<VaultTag>): Plan {
        val localByKey = existing.associateBy { it.name.trim().lowercase() }
        val createdKeyToTag = mutableMapOf<String, VaultTag>()
        val created = mutableListOf<VaultTag>()
        val idMap = mutableMapOf<String, String>()
        for (vt in incoming) {
            val key = vt.name.trim().lowercase()
            if (key.isEmpty() || !TagRules.isValidName(vt.name)) continue
            val local = localByKey[key]
            if (local != null) {
                idMap[vt.id] = local.id
            } else {
                // First occurrence wins; later duplicates map to the same tag.
                val target = createdKeyToTag.getOrPut(key) {
                    val normalized = vt.copy(color = TagColor.normalize(vt.color))
                    created.add(normalized)
                    normalized
                }
                idMap[vt.id] = target.id
            }
        }
        return Plan(idMap, created)
    }
}
