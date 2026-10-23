package com.vidplay.app.ui.vault

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.vidplay.app.VidPlayApp
import com.vidplay.app.data.db.PendingDeleteEntity
import com.vidplay.app.data.db.VaultItemEntity
import com.vidplay.app.data.vault.TransferState
import com.vidplay.app.data.vault.VaultTransferMonitor
import com.vidplay.app.data.vault.VaultWorkKeys
import com.vidplay.app.data.vault.VaultWorkScheduler
import com.vidplay.app.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class DeleteOriginalResult {
    data class IntentSenderReady(val sender: IntentSender) : DeleteOriginalResult()
    data object Deleted : DeleteOriginalResult()
    data class Failed(val message: String) : DeleteOriginalResult()
}

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as VidPlayApp).container
    private val vaultRepository = container.vaultRepository
    private val mediaRepository = container.mediaStoreRepository

    val items: StateFlow<List<VaultItemEntity>> = vaultRepository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingDeletes: StateFlow<List<PendingDeleteEntity>> = vaultRepository.observePendingDeletes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transfer: StateFlow<TransferState?> = VaultTransferMonitor.state

    private val _unlocked = MutableStateFlow(vaultRepository.isUnlocked())
    val unlocked: StateFlow<Boolean> = _unlocked

    private val _hasVault = MutableStateFlow(vaultRepository.hasVault())
    val hasVault: StateFlow<Boolean> = _hasVault

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun refreshState() {
        _unlocked.value = vaultRepository.isUnlocked()
        _hasVault.value = vaultRepository.hasVault()
    }

    fun setup(pin: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = vaultRepository.setup(pin)
            refreshState()
            onResult(result)
        }
    }

    fun unlockWithKeystore(onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = vaultRepository.unlockWithKeystore()
            refreshState()
            onResult(result)
        }
    }

    fun unlockWithPin(pin: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = vaultRepository.unlockWithPin(pin)
            refreshState()
            onResult(result)
        }
    }

    fun lock() {
        vaultRepository.lock()
        refreshState()
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val metadata = mediaRepository.queryOpenableMetadata(uri)
                    val name = metadata?.first ?: uri.lastPathSegment ?: "Vídeo"
                    val size = metadata?.second ?: -1L
                    val mime = mediaRepository.mimeType(uri)
                    mediaRepository.takePersistableRead(uri)
                    VaultWorkScheduler.enqueueImport(
                        context = getApplication(),
                        uri = uri,
                        displayName = name,
                        mimeType = mime,
                        size = size,
                        deleteOriginal = true,
                    )
                }
            }
        }
    }

    fun restore(item: VaultItemEntity, destinationUri: Uri? = null) {
        destinationUri?.let { mediaRepository.takePersistableWrite(it) }
        VaultWorkScheduler.enqueueExport(
            context = getApplication(),
            vaultId = item.id,
            destinationUri = destinationUri,
            autoDestination = destinationUri == null,
        )
    }

    fun delete(item: VaultItemEntity, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = vaultRepository.delete(item)
            onResult(result.isSuccess)
        }
    }

    fun play(context: Context, item: VaultItemEntity) {
        if (!vaultRepository.isUnlocked()) {
            _message.value = "Desbloquea el baúl primero"
            return
        }
        val all = items.value.ifEmpty { listOf(item) }
        val index = all.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        context.startActivity(
            PlayerActivity.intent(
                context = context,
                uris = all.map { vaultRepository.vaultUri(it.id) },
                titles = all.map { it.displayName },
                index = index,
                startPosition = item.lastPositionMs,
                mimeTypes = all.map { it.mimeType },
            ),
        )
    }

    fun thumbnail(id: String) = vaultRepository.readThumbnail(id)

    fun clearPending(id: Long) {
        viewModelScope.launch { vaultRepository.clearPendingDelete(id) }
    }

    suspend fun deleteOriginal(pending: PendingDeleteEntity): DeleteOriginalResult = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(pending.sourceUri) }.getOrNull()
            ?: return@withContext DeleteOriginalResult.Failed("URI no válida")
        if (Build.VERSION.SDK_INT >= 30 && mediaRepository.isMediaStoreUri(uri)) {
            val sender = mediaRepository.createDeleteRequest(listOf(uri))
            if (sender != null) return@withContext DeleteOriginalResult.IntentSenderReady(sender)
        }
        val resolver = getApplication<Application>().contentResolver
        try {
            val deleted = if (DocumentsContract.isDocumentUri(getApplication(), uri)) {
                DocumentsContract.deleteDocument(resolver, uri)
            } else {
                resolver.delete(uri, null, null) > 0
            }
            if (deleted) {
                vaultRepository.clearPendingDelete(pending.id)
                DeleteOriginalResult.Deleted
            } else {
                DeleteOriginalResult.Failed("El proveedor no permitió borrar el original")
            }
        } catch (recoverable: RecoverableSecurityException) {
            DeleteOriginalResult.IntentSenderReady(recoverable.userAction.actionIntent.intentSender)
        } catch (t: Throwable) {
            DeleteOriginalResult.Failed(t.message ?: "No se pudo borrar el original")
        }
    }

    fun clearTransfer() {
        VaultTransferMonitor.clear()
    }

    fun cancelTransfer() {
        val manager = WorkManager.getInstance(getApplication())
        manager.cancelUniqueWork(VaultWorkKeys.TAG_IMPORT)
        manager.cancelUniqueWork(VaultWorkKeys.TAG_EXPORT)
        VaultTransferMonitor.clear()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun setMessage(text: String) {
        _message.value = text
    }
}
