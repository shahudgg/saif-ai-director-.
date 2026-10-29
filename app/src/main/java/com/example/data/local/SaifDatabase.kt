package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, UserAccountEntity::class],
    version = 4,
    exportSchema = false
)
abstract class SaifDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userAccountDao(): UserAccountDao

    companion object {
        @Volatile
        private var INSTANCE: SaifDatabase? = null

        fun getDatabase(context: Context): SaifDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaifDatabase::class.java,
                    "saif_ai_database_v2"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
