package com.vidplay.app.data.vault

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TransferKind { IMPORT, EXPORT }

data class TransferState(
    val kind: TransferKind,
    val displayName: String,
    val progress: Float? = null,
    val running: Boolean = true,
    val error: String? = null,
    val vaultId: String? = null,
)

/** In-process status bus for import/export notifications and the vault UI. */
object VaultTransferMonitor {
    private val _state = MutableStateFlow<TransferState?>(null)
    val state: StateFlow<TransferState?> = _state.asStateFlow()

    fun start(kind: TransferKind, displayName: String) {
        _state.value = TransferState(kind = kind, displayName = displayName, progress = 0f)
    }

    fun progress(progress: Float?) {
        _state.value = _state.value?.copy(progress = progress)
    }

    fun success(vaultId: String? = null) {
        _state.value = _state.value?.copy(running = false, progress = 1f, vaultId = vaultId)
    }

    fun failure(message: String) {
        _state.value = _state.value?.copy(running = false, error = message)
    }

    fun clear() {
        _state.value = null
    }
}
