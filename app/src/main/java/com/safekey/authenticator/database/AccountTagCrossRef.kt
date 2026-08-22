package com.safekey.authenticator.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Many-to-many account/tag relation. Cascades only the relation, never accounts. */
@Entity(
    tableName = "account_tag_cross_ref",
    primaryKeys = ["accountId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class AccountTagCrossRef(
    val accountId: String,
    val tagId: String
)
