package com.vidplay.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [VaultItemEntity::class, PendingDeleteEntity::class, PlaybackPositionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun pendingDeleteDao(): PendingDeleteDao
    abstract fun playbackPositionDao(): PlaybackPositionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "vidplay.db")
                .build()
    }
}
