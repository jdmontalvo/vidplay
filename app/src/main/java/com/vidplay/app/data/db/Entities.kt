package com.vidplay.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val sha256: String,
    val lastPositionMs: Long = 0L,
    val thumbFile: String? = null,
)

@Entity(tableName = "pending_deletes")
data class PendingDeleteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val vaultId: String,
    val sourceUri: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val mediaKey: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)
