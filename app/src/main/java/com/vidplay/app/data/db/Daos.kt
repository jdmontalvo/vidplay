package com.vidplay.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    suspend fun getAll(): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: String): VaultItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VaultItemEntity)

    @Query("UPDATE vault_items SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun updatePosition(id: String, positionMs: Long)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PendingDeleteDao {
    @Query("SELECT * FROM pending_deletes ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PendingDeleteEntity>>

    @Query("SELECT * FROM pending_deletes ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingDeleteEntity>

    @Insert
    suspend fun insert(item: PendingDeleteEntity): Long

    @Query("DELETE FROM pending_deletes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM pending_deletes WHERE vaultId = :vaultId")
    suspend fun deleteForVault(vaultId: String)

    @Query("DELETE FROM pending_deletes WHERE sourceUri = :uri")
    suspend fun deleteByUri(uri: String)
}

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE mediaKey = :key")
    suspend fun get(key: String): PlaybackPositionEntity?

    @Upsert
    suspend fun upsert(position: PlaybackPositionEntity)

    @Query("DELETE FROM playback_positions WHERE mediaKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM playback_positions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latest(): PlaybackPositionEntity?
}
