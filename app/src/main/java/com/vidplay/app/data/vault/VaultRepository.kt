package com.vidplay.app.data.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.vidplay.app.data.db.AppDatabase
import com.vidplay.app.data.db.PendingDeleteEntity
import com.vidplay.app.data.db.VaultItemEntity
import com.vidplay.app.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID
import javax.crypto.SecretKey

data class VaultMediaInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val thumbnail: ByteArray?,
)

class VaultRepository(
    private val context: Context,
    private val database: AppDatabase,
    @Suppress("unused") private val settings: SettingsRepository,
) {
    private val resolver get() = context.contentResolver
    private val vaultDir = File(context.filesDir, "vault").apply { mkdirs() }
    private val keyManager = VaultKeyManager(vaultDir)

    init {
        // Incomplete transfers never contain a usable vault file.
        vaultDir.listFiles { file -> file.name.endsWith(".part") }?.forEach { it.delete() }
    }
    val session = VaultSession()
    private val itemDao get() = database.vaultItemDao()
    private val pendingDao get() = database.pendingDeleteDao()

    fun observeItems(): Flow<List<VaultItemEntity>> = itemDao.observeAll()
    fun observePendingDeletes(): Flow<List<PendingDeleteEntity>> = pendingDao.observeAll()

    fun hasVault(): Boolean = keyManager.hasVault()
    fun isUnlocked(): Boolean = session.isUnlocked()

    suspend fun setup(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dek = VaultCrypto.randomKey()
            keyManager.create(dek, pin)
            session.unlock(dek)
        }
    }

    /**
     * The biometric prompt must have succeeded before this call. The Keystore
     * key itself is not user-auth-bound, so the app still works with a recovery
     * PIN if biometrics or the screen lock change.
     */
    suspend fun unlockWithKeystore(): Result<Unit> = withContext(Dispatchers.IO) {
        val dek = keyManager.unwrapWithKeystore()
            ?: return@withContext Result.failure(IllegalStateException("No se pudo abrir el baúl"))
        session.unlock(dek)
        Result.success(Unit)
    }

    /**
     * WorkManager can restart the process after the UI has gone away. The
     * Keystore copy is intentionally not auth-bound, so a user-initiated
     * import/export can finish without showing a biometric prompt. The UI gate
     * remains the biometric/PIN screen.
     */
    suspend fun unlockForBackgroundWork(): Boolean = withContext(Dispatchers.IO) {
        if (session.isUnlocked()) return@withContext true
        if (!keyManager.hasVault()) return@withContext false
        val dek = keyManager.unwrapWithKeystore() ?: return@withContext false
        session.unlock(dek)
        true
    }

    suspend fun unlockWithPin(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        val dek = keyManager.unwrapWithPin(pin)
            ?: return@withContext Result.failure(IllegalArgumentException("PIN incorrecto"))
        session.unlock(dek)
        Result.success(Unit)
    }

    fun lock() {
        session.lock()
    }

    fun lockIfIdle() {
        if (!session.playbackActive) session.lock()
    }

    fun keyOrNull(): SecretKey? = session.keyOrNull()

    fun vaultUri(id: String): Uri = Uri.parse("vault://media/$id")

    fun idFromUri(uri: Uri): String? {
        if (uri.scheme != "vault") return null
        return uri.lastPathSegment
    }

    fun isVaultUri(uri: Uri): Boolean = uri.scheme == "vault"

    fun fileFor(id: String): File = File(vaultDir, "$id.vpv")

    fun thumbFileFor(id: String): File = File(vaultDir, "$id.vpt")

    fun headerFor(id: String): VaultCrypto.Header = VaultCrypto.readHeader(fileFor(id))

    fun readThumbnail(id: String): Bitmap? {
        val key = session.keyOrNull() ?: return null
        val file = thumbFileFor(id)
        if (!file.exists()) return null
        return try {
            val bytes = VaultCrypto.decryptBytes(file.readBytes(), key)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun import(
        uri: Uri,
        displayName: String,
        mimeType: String,
        expectedSize: Long,
        onProgress: suspend (read: Long, total: Long) -> Unit = { _, _ -> },
    ): VaultItemEntity = withContext(Dispatchers.IO) {
        val key = session.requireKey()
        val id = UUID.randomUUID().toString()
        val target = fileFor(id)
        val thumb = thumbFileFor(id)
        var created = false
        try {
            val info = readMediaInfo(uri, mimeType)
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("No se pudo abrir el vídeo de origen")
            input.use { stream ->
                VaultCrypto.encrypt(
                    input = stream,
                    target = target,
                    key = key,
                    expectedSize = expectedSize,
                ) { read -> onProgress(read, expectedSize) }
            }
            created = true
            val header = VaultCrypto.readHeader(target)

            info.thumbnail?.let { jpeg ->
                val encryptedThumb = VaultCrypto.encryptBytes(jpeg, key)
                thumb.writeBytes(encryptedThumb)
            }

            val entity = VaultItemEntity(
                id = id,
                displayName = displayName.ifBlank { "Vídeo ${id.take(4)}" },
                mimeType = info.mimeType.ifBlank { mimeType },
                sizeBytes = header.plainSize,
                durationMs = info.durationMs,
                width = info.width,
                height = info.height,
                createdAt = System.currentTimeMillis(),
                sha256 = VaultCrypto.sha256Hex(header.sha256),
                thumbFile = if (thumb.exists()) thumb.name else null,
            )
            itemDao.insert(entity)
            onProgress(header.plainSize, header.plainSize)
            entity
        } catch (t: Throwable) {
            if (created) {
                target.delete()
                thumb.delete()
            }
            throw t
        }
    }

    suspend fun item(id: String): VaultItemEntity? = withContext(Dispatchers.IO) { itemDao.getById(id) }

    suspend fun updatePosition(id: String, positionMs: Long) = withContext(Dispatchers.IO) {
        itemDao.updatePosition(id, positionMs.coerceAtLeast(0L))
        session.touch()
    }

    suspend fun export(
        item: VaultItemEntity,
        destinationUri: Uri? = null,
        autoDestination: Boolean = true,
        onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val key = session.keyOrNull()
            ?: return@withContext Result.failure(VaultLockedException())
        val source = fileFor(item.id)
        if (!source.exists()) return@withContext Result.failure(IllegalStateException("Vídeo no encontrado"))
        var mediaStoreUri: Uri? = null
        try {
            if (destinationUri != null) {
                val output = resolver.openOutputStream(destinationUri)
                    ?: throw IllegalStateException("No se pudo crear el destino")
                output.use { out ->
                    VaultCrypto.decrypt(source, out, key, verifyHash = true, onProgress = onProgress)
                }
            } else if (autoDestination && Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, safeFileName(item.displayName))
                    put(MediaStore.Video.Media.MIME_TYPE, item.mimeType)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VidPlay")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    values,
                ) ?: throw IllegalStateException("No se pudo crear el destino")
                mediaStoreUri = uri
                val output = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("No se pudo escribir en Movies/VidPlay")
                output.use { out ->
                    VaultCrypto.decrypt(source, out, key, verifyHash = true, onProgress = onProgress)
                }
                resolver.update(uri, ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }, null, null)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "VidPlay",
                ).apply { mkdirs() }
                val target = File(dir, safeFileName(item.displayName))
                FileOutputStream(target).use { out ->
                    VaultCrypto.decrypt(source, out, key, verifyHash = true, onProgress = onProgress)
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(target.absolutePath),
                    arrayOf(item.mimeType),
                    null,
                )
            }
            deleteFiles(item)
            itemDao.deleteById(item.id)
            pendingDao.deleteForVault(item.id)
            Result.success(Unit)
        } catch (t: Throwable) {
            mediaStoreUri?.let {
                try {
                    resolver.delete(it, null, null)
                } catch (_: Exception) {
                    // Best effort.
                }
            }
            destinationUri?.let {
                try {
                    resolver.delete(it, null, null)
                } catch (_: Exception) {
                    // Best effort.
                }
            }
            Result.failure(t)
        }
    }

    suspend fun delete(item: VaultItemEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            deleteFiles(item)
            itemDao.deleteById(item.id)
            pendingDao.deleteForVault(item.id)
        }
    }

    suspend fun addPendingDelete(vaultId: String, sourceUri: Uri, displayName: String) =
        withContext(Dispatchers.IO) {
            pendingDao.insert(
                PendingDeleteEntity(
                    vaultId = vaultId,
                    sourceUri = sourceUri.toString(),
                    displayName = displayName,
                ),
            )
        }

    suspend fun clearPendingDelete(id: Long) = withContext(Dispatchers.IO) {
        pendingDao.deleteById(id)
    }

    suspend fun clearPendingDeleteForUri(uri: Uri) = withContext(Dispatchers.IO) {
        pendingDao.deleteByUri(uri.toString())
    }

    suspend fun pendingDeletes(): List<PendingDeleteEntity> = withContext(Dispatchers.IO) {
        pendingDao.getAll()
    }

    fun openOutputStream(uri: Uri): OutputStream? = resolver.openOutputStream(uri)

    private fun deleteFiles(item: VaultItemEntity) {
        fileFor(item.id).delete()
        thumbFileFor(item.id).delete()
        item.thumbFile?.let { File(vaultDir, it).delete() }
    }

    private fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { "video-${System.currentTimeMillis()}.mp4" }
    }

    private fun readMediaInfo(uri: Uri, fallbackMime: String): VaultMediaInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.substringBefore(';')?.trim()
                ?.takeIf { it.startsWith("video/") }
                ?: fallbackMime
            val bitmap = try {
                val at = if (duration > 0) (duration / 10) * 1000 else 0
                retriever.getFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
            } catch (_: Exception) {
                null
            }
            val thumbnail = bitmap?.let { createThumbnailJpeg(it) }
            VaultMediaInfo(
                durationMs = duration,
                width = width,
                height = height,
                mimeType = mime,
                thumbnail = thumbnail,
            )
        } catch (_: Exception) {
            VaultMediaInfo(0L, 0, 0, fallbackMime, null)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Best effort.
            }
        }
    }

    private fun createThumbnailJpeg(bitmap: Bitmap): ByteArray? {
        return try {
            val scale = maxOf(THUMB_WIDTH.toFloat() / bitmap.width, THUMB_HEIGHT.toFloat() / bitmap.height)
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(THUMB_WIDTH),
                (bitmap.height * scale).toInt().coerceAtLeast(THUMB_HEIGHT),
                true,
            )
            val x = ((scaled.width - THUMB_WIDTH) / 2).coerceAtLeast(0)
            val y = ((scaled.height - THUMB_HEIGHT) / 2).coerceAtLeast(0)
            val cropped = Bitmap.createBitmap(scaled, x, y, THUMB_WIDTH, THUMB_HEIGHT)
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 84, out)
            if (scaled !== cropped) scaled.recycle()
            cropped.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val THUMB_WIDTH = 480
        private const val THUMB_HEIGHT = 270
    }
}
