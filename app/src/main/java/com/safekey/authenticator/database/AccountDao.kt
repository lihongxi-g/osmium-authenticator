package com.safekey.authenticator.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("UPDATE accounts SET copyCount = copyCount + 1 WHERE id = :id")
    suspend fun incrementCopyCount(id: String)

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM accounts")
    suspend fun maxSortOrder(): Long

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
