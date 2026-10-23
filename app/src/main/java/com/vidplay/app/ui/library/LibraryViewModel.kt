package com.vidplay.app.ui.library

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vidplay.app.VidPlayApp
import com.vidplay.app.data.media.FolderItem
import com.vidplay.app.data.media.MediaPermissions
import com.vidplay.app.data.media.VideoItem
import com.vidplay.app.data.settings.SortOrder
import com.vidplay.app.data.vault.VaultWorkScheduler
import com.vidplay.app.player.PlayerActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as VidPlayApp).container
    private val mediaRepository = container.mediaStoreRepository
    private val vaultRepository = container.vaultRepository
    private val refreshTick = MutableStateFlow(0L)

    val query = MutableStateFlow("")
    val sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val permissionGranted = MutableStateFlow(MediaPermissions.hasAnyAccess(app))
    val partialAccess = MutableStateFlow(MediaPermissions.hasPartialAccess(app))

    private val allVideos = refreshTick.flatMapLatest {
        mediaRepository.observeVideos()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<FolderItem>> = allVideos
        .combine(sortOrder) { videos, order ->
            mediaRepository.folders(mediaRepository.sort(videos, order))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val videos: StateFlow<List<VideoItem>> = allVideos
        .combine(query) { videos, text -> videos to text }
        .combine(sortOrder) { pair, order ->
            val filtered = if (pair.second.isBlank()) {
                pair.first
            } else {
                pair.first.filter {
                    it.displayName.contains(pair.second, ignoreCase = true) ||
                        it.bucketName.contains(pair.second, ignoreCase = true)
                }
            }
            mediaRepository.sort(filtered, order)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { settings ->
                sortOrder.value = settings.sortOrder
            }
        }
    }

    fun refresh() {
        permissionGranted.value = MediaPermissions.hasAnyAccess(getApplication())
        partialAccess.value = MediaPermissions.hasPartialAccess(getApplication())
        refreshTick.value = refreshTick.value + 1
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSortOrder(order: SortOrder) {
        sortOrder.value = order
        viewModelScope.launch { container.settingsRepository.setSortOrder(order) }
    }

    fun play(context: Context, item: VideoItem) {
        val list = videos.value.ifEmpty { listOf(item) }
        val index = list.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                uris = list.map { it.uri },
                titles = list.map { it.displayName },
                index = index,
                mimeTypes = list.map { it.mimeType },
            ),
        )
    }

    fun playFolder(context: Context, videos: List<VideoItem>, index: Int) {
        if (videos.isEmpty()) return
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                uris = videos.map { it.uri },
                titles = videos.map { it.displayName },
                index = index.coerceIn(0, videos.lastIndex),
                mimeTypes = videos.map { it.mimeType },
            ),
        )
    }

    fun openFile(context: Context, uri: Uri) {
        val name = mediaRepository.queryOpenableMetadata(uri)?.first
            ?: uri.lastPathSegment
            ?: "Vídeo"
        mediaRepository.takePersistableRead(uri)
        context.startActivity(
            PlayerActivity.intent(
                context,
                listOf(uri),
                listOf(name),
                mimeTypes = listOf(mediaRepository.mimeType(uri)),
            ),
        )
    }

    fun hide(context: Context, item: VideoItem): Boolean {
        if (!vaultRepository.hasVault() || !vaultRepository.isUnlocked()) return false
        VaultWorkScheduler.enqueueImport(
            context = context,
            uri = item.uri,
            displayName = item.displayName,
            mimeType = item.mimeType,
            size = item.sizeBytes,
            deleteOriginal = true,
        )
        return true
    }

    fun deleteRequest(item: VideoItem): IntentSender? =
        mediaRepository.createDeleteRequest(listOf(item.uri))

    suspend fun deleteDirect(item: VideoItem): Boolean =
        mediaRepository.deleteDirect(item.uri)

    fun vaultReady(): Boolean = vaultRepository.hasVault() && vaultRepository.isUnlocked()

    fun mimeType(uri: Uri): String = mediaRepository.mimeType(uri)
}
