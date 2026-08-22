package com.safekey.authenticator.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TagEntity?

    @Query("SELECT id, name, color, createdAt, updatedAt FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity)

    @Query("UPDATE tags SET name = :name, color = :color, updatedAt = :updatedAt WHERE id = :id")
    suspend fun update(id: String, name: String, color: String, updatedAt: Long)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM account_tag_cross_ref WHERE accountId IN (:accountIds)")
    suspend fun getRefsForAccounts(accountIds: List<String>): List<AccountTagCrossRef>

    @Query("SELECT * FROM account_tag_cross_ref")
    fun observeAllRefs(): Flow<List<AccountTagCrossRef>>

    @Query("SELECT * FROM account_tag_cross_ref WHERE accountId = :accountId")
    suspend fun getRefsForAccount(accountId: String): List<AccountTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<AccountTagCrossRef>)

    @Query("DELETE FROM account_tag_cross_ref WHERE accountId = :accountId")
    suspend fun deleteRefsForAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM account_tag_cross_ref WHERE tagId = :tagId")
    suspend fun countAccounts(tagId: String): Int
}
