package com.vidplay.app.data.vault

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vidplay.app.R
import com.vidplay.app.VidPlayApp
import com.vidplay.app.util.Notifications
import java.util.UUID

object VaultWorkKeys {
    const val URI = "uri"
    const val NAME = "name"
    const val MIME = "mime"
    const val SIZE = "size"
    const val DELETE_ORIGINAL = "delete_original"
    const val VAULT_ID = "vault_id"
    const val DESTINATION_URI = "destination_uri"
    const val AUTO_DESTINATION = "auto_destination"
    const val PROGRESS = "progress"
    const val ERROR = "error"
    const val TAG_IMPORT = "vault_import"
    const val TAG_EXPORT = "vault_export"
}

class VaultImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VidPlayApp ?: return Result.failure()
        val repo = app.container.vaultRepository
        val uri = inputData.getString(VaultWorkKeys.URI)?.let(Uri::parse)
            ?: return Result.failure(workDataOf(VaultWorkKeys.ERROR to "URI no válida"))
        val name = inputData.getString(VaultWorkKeys.NAME) ?: "Vídeo"
        val mime = inputData.getString(VaultWorkKeys.MIME) ?: "video/*"
        val size = inputData.getLong(VaultWorkKeys.SIZE, -1L)
        val deleteOriginal = inputData.getBoolean(VaultWorkKeys.DELETE_ORIGINAL, true)
        val wasUnlocked = repo.isUnlocked()

        if (!repo.unlockForBackgroundWork()) {
            VaultTransferMonitor.failure("Desbloquea el baúl para importar")
            return Result.failure(workDataOf(VaultWorkKeys.ERROR to "Baúl bloqueado"))
        }

        VaultTransferMonitor.start(TransferKind.IMPORT, name)
        var lastNotification = 0L
        return try {
            try {
                setForeground(createForegroundInfo(null, name))
            } catch (_: Exception) {
                // Continue without a foreground notification if the OS blocks it.
            }
            val item = repo.import(uri, name, mime, size) { read, total ->
                val progress = if (total > 0) {
                    ((read.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                setProgress(workDataOf(VaultWorkKeys.PROGRESS to (progress ?: 0)))
                VaultTransferMonitor.progress(progress?.let { it / 100f })
                val now = SystemClock.elapsedRealtime()
                if (now - lastNotification > 900) {
                    lastNotification = now
                    try {
                        setForeground(createForegroundInfo(progress, name))
                    } catch (_: Exception) {
                        // The foreground notification can be rejected if notifications are disabled.
                    }
                }
            }

            if (deleteOriginal) {
                val deleted = tryDeleteOriginal(uri)
                if (!deleted) {
                    repo.addPendingDelete(item.id, uri, name)
                }
            }

            VaultTransferMonitor.success(item.id)
            notifyFinished(
                text = applicationContext.getString(R.string.notification_import_done),
            )
            Result.success(workDataOf(VaultWorkKeys.VAULT_ID to item.id))
        } catch (t: Throwable) {
            val message = t.message ?: "No se pudo ocultar el vídeo"
            VaultTransferMonitor.failure(message)
            notifyFinished(
                "No se pudo ocultar el vídeo. Abre VidCofre para reintentar.",
                failed = true,
            )
            Result.failure(workDataOf(VaultWorkKeys.ERROR to message))
        } finally {
            if (!wasUnlocked || !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                repo.lock()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val name = inputData.getString(VaultWorkKeys.NAME) ?: "Vídeo"
        return createForegroundInfo(null, name)
    }

    private fun createForegroundInfo(progress: Int?, @Suppress("UNUSED_PARAMETER") name: String): ForegroundInfo {
        val notification = Notifications.transferNotification(
            context = applicationContext,
            title = "Ocultando en el baúl",
            text = if (progress != null) {
                "Vídeo del baúl · $progress%"
            } else {
                "Preparando el baúl cifrado…"
            },
            progress = progress,
            indeterminate = progress == null,
        )
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun tryDeleteOriginal(uri: Uri): Boolean {
        val resolver = applicationContext.contentResolver
        if (DocumentsContract.isDocumentUri(applicationContext, uri)) {
            return try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (_: Exception) {
                false
            }
        }
        if (Build.VERSION.SDK_INT < 30) {
            return try {
                resolver.delete(uri, null, null) > 0
            } catch (_: Exception) {
                false
            }
        }
        return false
    }

    private fun notifyFinished(text: String, failed: Boolean = false) {
        try {
            val notification = Notifications.transferNotification(
                context = applicationContext,
                title = if (failed) "No se pudo ocultar" else "Vídeo ocultado",
                text = text,
                progress = if (failed) null else 100,
                ongoing = false,
            )
            NotificationManagerCompat.from(applicationContext).notify(FINISHED_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied.
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2101
        private const val FINISHED_ID = 2102
    }
}

class VaultExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? VidPlayApp ?: return Result.failure()
        val repo = app.container.vaultRepository
        val vaultId = inputData.getString(VaultWorkKeys.VAULT_ID)
            ?: return Result.failure(workDataOf(VaultWorkKeys.ERROR to "Vídeo no válido"))
        val destination = inputData.getString(VaultWorkKeys.DESTINATION_URI)?.let(Uri::parse)
        val auto = inputData.getBoolean(VaultWorkKeys.AUTO_DESTINATION, destination == null)
        val item = repo.item(vaultId)
            ?: return Result.failure(workDataOf(VaultWorkKeys.ERROR to "Vídeo no encontrado"))
        val wasUnlocked = repo.isUnlocked()
        if (!repo.unlockForBackgroundWork()) {
            return Result.failure(workDataOf(VaultWorkKeys.ERROR to "Baúl bloqueado"))
        }

        VaultTransferMonitor.start(TransferKind.EXPORT, item.displayName)
        var lastNotification = 0L
        return try {
            try {
                setForeground(createForegroundInfo(null, item.displayName))
            } catch (_: Exception) {
                // Continue without a foreground notification if the OS blocks it.
            }
            val result = repo.export(item, destination, auto) { done, total ->
                val progress = if (total > 0) {
                    ((done.toDouble() / total.toDouble()) * 100.0).toInt().coerceIn(0, 100)
                } else {
                    null
                }
                setProgress(workDataOf(VaultWorkKeys.PROGRESS to (progress ?: 0)))
                VaultTransferMonitor.progress(progress?.let { it / 100f })
                val now = SystemClock.elapsedRealtime()
                if (now - lastNotification > 900) {
                    lastNotification = now
                    try {
                        setForeground(createForegroundInfo(progress, item.displayName))
                    } catch (_: Exception) {
                        // The foreground notification can be rejected if notifications are disabled.
                    }
                }
            }
            result.fold(
                onSuccess = {
                    VaultTransferMonitor.success()
                    notifyFinished(
                        applicationContext.getString(R.string.notification_export_done),
                        failed = false,
                    )
                    Result.success()
                },
                onFailure = { t ->
                    val message = t.message ?: "No se pudo restaurar el vídeo"
                    VaultTransferMonitor.failure(message)
                    notifyFinished(
                        "No se pudo restaurar el vídeo. Abre VidCofre para reintentar.",
                        failed = true,
                    )
                    Result.failure(workDataOf(VaultWorkKeys.ERROR to message))
                },
            )
        } catch (t: Throwable) {
            val message = t.message ?: "No se pudo restaurar el vídeo"
            VaultTransferMonitor.failure(message)
            notifyFinished(
                "No se pudo restaurar el vídeo. Abre VidCofre para reintentar.",
                failed = true,
            )
            Result.failure(workDataOf(VaultWorkKeys.ERROR to message))
        } finally {
            if (!wasUnlocked || !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                repo.lock()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val id = inputData.getString(VaultWorkKeys.VAULT_ID)
        val item = id?.let { (applicationContext as? VidPlayApp)?.container?.vaultRepository?.item(it) }
        return createForegroundInfo(null, item?.displayName ?: "Vídeo")
    }

    private fun createForegroundInfo(progress: Int?, @Suppress("UNUSED_PARAMETER") name: String): ForegroundInfo {
        val notification = Notifications.transferNotification(
            context = applicationContext,
            title = "Restaurando vídeo",
            text = if (progress != null) {
                "Vídeo del baúl · $progress%"
            } else {
                "Restaurando vídeo del baúl…"
            },
            progress = progress,
            indeterminate = progress == null,
        )
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyFinished(text: String, failed: Boolean) {
        try {
            val notification = Notifications.transferNotification(
                context = applicationContext,
                title = if (failed) "No se pudo restaurar" else "Vídeo restaurado",
                text = text,
                progress = if (failed) null else 100,
                ongoing = false,
            )
            NotificationManagerCompat.from(applicationContext).notify(FINISHED_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied.
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2201
        private const val FINISHED_ID = 2202
    }
}

object VaultWorkScheduler {
    fun enqueueImport(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
        size: Long,
        deleteOriginal: Boolean,
    ): UUID {
        val request = OneTimeWorkRequestBuilder<VaultImportWorker>()
            .setInputData(
                workDataOf(
                    VaultWorkKeys.URI to uri.toString(),
                    VaultWorkKeys.NAME to displayName,
                    VaultWorkKeys.MIME to mimeType,
                    VaultWorkKeys.SIZE to size,
                    VaultWorkKeys.DELETE_ORIGINAL to deleteOriginal,
                ),
            )
            .addTag(VaultWorkKeys.TAG_IMPORT)
            .build()
        VaultTransferMonitor.start(TransferKind.IMPORT, displayName)
        WorkManager.getInstance(context)
            .beginUniqueWork(VaultWorkKeys.TAG_IMPORT, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            .enqueue()
        return request.id
    }

    fun enqueueExport(
        context: Context,
        vaultId: String,
        destinationUri: Uri? = null,
        autoDestination: Boolean = destinationUri == null,
    ): UUID {
        val request = OneTimeWorkRequestBuilder<VaultExportWorker>()
            .setInputData(
                workDataOf(
                    VaultWorkKeys.VAULT_ID to vaultId,
                    VaultWorkKeys.DESTINATION_URI to destinationUri?.toString(),
                    VaultWorkKeys.AUTO_DESTINATION to autoDestination,
                ),
            )
            .addTag(VaultWorkKeys.TAG_EXPORT)
            .build()
        VaultTransferMonitor.start(TransferKind.EXPORT, "Vídeo del baúl")
        WorkManager.getInstance(context)
            .beginUniqueWork(VaultWorkKeys.TAG_EXPORT, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            .enqueue()
        return request.id
    }
}
