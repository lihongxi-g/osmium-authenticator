package com.safekey.authenticator.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Local tag metadata. Account relations are stored separately in [AccountTagCrossRef]. */
@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val createdAt: Long,
    val updatedAt: Long
)
