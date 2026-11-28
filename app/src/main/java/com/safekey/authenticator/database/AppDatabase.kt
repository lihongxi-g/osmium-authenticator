package com.safekey.authenticator.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AccountEntity::class, TagEntity::class, AccountTagCrossRef::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun tagDao(): TagDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN copyCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT 'totp'")
                db.execSQL("ALTER TABLE accounts ADD COLUMN counter INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS tags (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        color TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS account_tag_cross_ref (
                        accountId TEXT NOT NULL,
                        tagId TEXT NOT NULL,
                        PRIMARY KEY(accountId, tagId),
                        FOREIGN KEY(accountId) REFERENCES accounts(id) ON DELETE CASCADE,
                        FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_account_tag_cross_ref_tagId ON account_tag_cross_ref(tagId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Optional entry icon, encrypted per field. Nullable: existing
                // rows keep no icon and the UI falls back to text-only cards.
                db.execSQL("ALTER TABLE accounts ADD COLUMN iconMime TEXT")
                db.execSQL("ALTER TABLE accounts ADD COLUMN iconIv TEXT")
                db.execSQL("ALTER TABLE accounts ADD COLUMN iconCiphertext TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safekey.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { instance = it }
            }
    }
}
