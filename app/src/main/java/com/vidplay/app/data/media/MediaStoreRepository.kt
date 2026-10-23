package com.vidplay.app.data.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VideoItem(
    val uri: Uri,
    val id: Long,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val bucketId: String,
    val bucketName: String,
    val relativePath: String,
    val width: Int,
    val height: Int,
    val mimeType: String,
) {
    val mediaKey: String get() = uri.toString()
}

data class FolderItem(
    val bucketId: String,
    val name: String,
    val videos: List<VideoItem>,
) {
    val count: Int get() = videos.size
    val cover: VideoItem? get() = videos.firstOrNull()
}

object MediaPermissions {
    fun requiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
            Build.VERSION.SDK_INT <= 28 -> arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    fun hasFullAccess(context: Context): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val readGranted = ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!readGranted) return false
        if (Build.VERSION.SDK_INT <= 28) {
            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!writeGranted) return false
        }
        return true
    }

    fun hasPartialAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun hasAnyAccess(context: Context): Boolean = hasFullAccess(context) || hasPartialAccess(context)
}

class MediaStoreRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun observeVideos(): Flow<List<VideoItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                launch(Dispatchers.IO) { trySend(queryVideos()) }
            }
        }
        resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        launch(Dispatchers.IO) { trySend(queryVideos()) }
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    suspend fun videos(): List<VideoItem> = withContext(Dispatchers.IO) { queryVideos() }

    private fun queryVideos(): List<VideoItem> {
        if (!MediaPermissions.hasAnyAccess(context)) return emptyList()
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.BUCKET_ID)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= 29) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()

        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val result = mutableListOf<VideoItem>()
        try {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val bucketIdCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val relativeCol = if (Build.VERSION.SDK_INT >= 29) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    -1
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Vídeo"
                    result += VideoItem(
                        uri = ContentUris.withAppendedId(collection, id),
                        id = id,
                        displayName = name,
                        durationMs = cursor.getLong(durationCol).coerceAtLeast(0L),
                        sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
                        dateAddedMs = cursor.getLong(dateCol) * 1000L,
                        bucketId = cursor.getString(bucketIdCol) ?: "0",
                        bucketName = cursor.getString(bucketNameCol) ?: "Sin carpeta",
                        relativePath = if (relativeCol >= 0) cursor.getString(relativeCol) ?: "" else "",
                        width = cursor.getInt(widthCol),
                        height = cursor.getInt(heightCol),
                        mimeType = cursor.getString(mimeCol) ?: "video/*",
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: IllegalArgumentException) {
            return emptyList()
        }
        return result
    }

    fun sort(videos: List<VideoItem>, order: com.vidplay.app.data.settings.SortOrder): List<VideoItem> {
        return when (order) {
            com.vidplay.app.data.settings.SortOrder.DATE_DESC -> videos.sortedByDescending { it.dateAddedMs }
            com.vidplay.app.data.settings.SortOrder.DATE_ASC -> videos.sortedBy { it.dateAddedMs }
            com.vidplay.app.data.settings.SortOrder.NAME_ASC -> videos.sortedBy { it.displayName.lowercase() }
            com.vidplay.app.data.settings.SortOrder.NAME_DESC -> videos.sortedByDescending { it.displayName.lowercase() }
            com.vidplay.app.data.settings.SortOrder.SIZE_DESC -> videos.sortedByDescending { it.sizeBytes }
            com.vidplay.app.data.settings.SortOrder.DURATION_DESC -> videos.sortedByDescending { it.durationMs }
        }
    }

    fun folders(videos: List<VideoItem>): List<FolderItem> {
        return videos.groupBy { it.bucketId }
            .map { (bucketId, items) ->
                FolderItem(
                    bucketId = bucketId,
                    name = items.firstOrNull()?.bucketName ?: "Sin carpeta",
                    videos = items.sortedByDescending { it.dateAddedMs },
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun createDeleteRequest(uris: List<Uri>): IntentSender? {
        if (Build.VERSION.SDK_INT < 30 || uris.isEmpty()) return null
        return try {
            MediaStore.createDeleteRequest(resolver, uris).intentSender
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteDirect(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.delete(uri, null, null) > 0
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isMediaStoreUri(uri: Uri): Boolean {
        return uri.authority == MediaStore.AUTHORITY
    }

    fun queryOpenableMetadata(uri: Uri): Pair<String, Long>? {
        return try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (!cursor.moveToFirst()) return null
                    val name = cursor.getString(0) ?: "video.mp4"
                    val size = if (cursor.isNull(1)) -1L else cursor.getLong(1)
                    name to size
                }
        } catch (_: Exception) {
            null
        }
    }

    fun mimeType(uri: Uri): String = resolver.getType(uri) ?: "video/*"

    fun takePersistableRead(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The URI is not persistable; the import worker still runs in this process.
        }
    }

    fun takePersistableWrite(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // CreateDocument grants are usually valid for the current process.
        }
    }

    fun defaultMovieDestination(name: String): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VidPlay")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return try {
            resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (_: Exception) {
            null
        }
    }

    fun publishPending(uri: Uri) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
        } catch (_: Exception) {
            // Best effort.
        }
    }

    fun deletePending(uri: Uri) {
        try {
            resolver.delete(uri, null, null)
        } catch (_: Exception) {
            // Best effort.
        }
    }

    fun legacyMoviesDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VidPlay")
}
