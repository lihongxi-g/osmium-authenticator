package com.safekey.authenticator.repository

import com.safekey.authenticator.database.AccountTagCrossRef
import com.safekey.authenticator.database.TagDao
import com.safekey.authenticator.database.TagEntity
import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.tags.TagColor
import com.safekey.authenticator.tags.TagRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TagRepository(private val dao: TagDao) {
    val tags: Flow<List<Tag>> = dao.observeAll().map { list -> list.map { Tag(it.id, it.name, it.color, it.createdAt, it.updatedAt) } }

    val accountTagIds: Flow<Map<String, Set<String>>> = dao.observeAllRefs().map { refs ->
        refs.groupBy { it.accountId }.mapValues { (_, values) -> values.map { it.tagId }.toSet() }
    }

    suspend fun create(name: String, color: String = TagColor.BLUE): Tag {
        val clean = validate(name)
        val now = System.currentTimeMillis()
        val tag = Tag(UUID.randomUUID().toString(), clean, color.takeIf { it in TagColor.ALL } ?: TagColor.BLUE, now, now)
        dao.insert(tag.toEntity())
        return tag
    }

    suspend fun createImported(id: String, name: String, color: String): Tag {
        val clean = validate(name)
        val tag = Tag(id, clean, color.takeIf { it in TagColor.ALL } ?: TagColor.BLUE, System.currentTimeMillis(), System.currentTimeMillis())
        dao.insert(tag.toEntity())
        return dao.getByName(clean)!!.let { Tag(it.id, it.name, it.color, it.createdAt, it.updatedAt) }
    }
    suspend fun update(tag: Tag, name: String, color: String): Tag {
        val clean = validate(name, tag.id)
        val updated = tag.copy(name = clean, color = color.takeIf { it in TagColor.ALL } ?: tag.color, updatedAt = System.currentTimeMillis())
        dao.update(updated.id, updated.name, updated.color, updated.updatedAt)
        return updated
    }

    suspend fun delete(tag: Tag) {
        dao.getById(tag.id)?.let { dao.delete(it) }
    }

    suspend fun setAccountTags(accountId: String, tagIds: Set<String>) {
        dao.deleteRefsForAccount(accountId)
        dao.insertRefs(tagIds.map { AccountTagCrossRef(accountId, it) })
    }

    suspend fun getAccountTagIds(accountId: String): Set<String> = dao.getRefsForAccount(accountId).map { it.tagId }.toSet()

    private suspend fun validate(raw: String, excludeId: String = ""): String {
        val clean = TagRules.normalizeName(raw)
        require(TagRules.isValidName(clean)) { "Invalid tag name" }
        val existing = dao.getByName(clean)
        require(existing == null || existing.id == excludeId) { "Duplicate tag name" }
        return clean
    }

    private fun Tag.toEntity() = TagEntity(id, name, color, createdAt, updatedAt)
}
