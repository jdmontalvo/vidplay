package com.vidplay.app.data.vault

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.RandomAccessFile
import javax.crypto.SecretKey

/**
 * Media3 DataSource that decrypts the vault chunk by chunk. No plaintext is
 * written to disk; seeking loads only the required 1 MiB chunk.
 */
class VaultDataSource(private val repository: VaultRepository) : DataSource {

    private var raf: RandomAccessFile? = null
    private var header: VaultCrypto.Header? = null
    private var key: SecretKey? = null
    private var position: Long = 0L
    private var currentIndex: Long = -1L
    private var currentChunk: ByteArray = ByteArray(0)
    private var sourceUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        val id = repository.idFromUri(dataSpec.uri)
            ?: throw IOException("URI de baúl no válido")
        val dek = repository.keyOrNull()
            ?: throw IOException("El baúl está bloqueado")
        val file = repository.fileFor(id)
        if (!file.exists()) throw IOException("Vídeo del baúl no encontrado")
        val parsed = repository.headerFor(id)
        raf = RandomAccessFile(file, "r")
        header = parsed
        key = dek
        sourceUri = repository.vaultUri(id)
        position = dataSpec.position.coerceAtLeast(0L)
        currentIndex = -1L
        currentChunk = ByteArray(0)
        return (parsed.plainSize - position).coerceAtLeast(0L)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val header = header ?: return C.RESULT_END_OF_INPUT
        if (position >= header.plainSize) return C.RESULT_END_OF_INPUT
        var written = 0
        while (written < length && position < header.plainSize) {
            val index = position / header.chunkSize
            val intra = (position % header.chunkSize).toInt()
            if (index != currentIndex) {
                loadChunk(index)
            }
            val chunk = currentChunk
            if (intra >= chunk.size) break
            val available = chunk.size - intra
            val copy = minOf(available, length - written)
            System.arraycopy(chunk, intra, buffer, offset + written, copy)
            written += copy
            position += copy
        }
        return if (written == 0) C.RESULT_END_OF_INPUT else written
    }

    private fun loadChunk(index: Long) {
        val raf = raf ?: throw IOException("DataSource cerrado")
        val header = header ?: throw IOException("Cabecera del baúl no disponible")
        val key = key ?: throw IOException("Clave del baúl no disponible")
        currentChunk = try {
            VaultCrypto.decryptChunk(raf, header, index, key)
        } catch (t: Throwable) {
            throw IOException("No se pudo descifrar el bloque del baúl", t)
        }
        currentIndex = index
    }

    override fun getUri(): Uri? = sourceUri

    override fun addTransferListener(transferListener: TransferListener) {
        // Local vault reads do not need byte-level transfer accounting.
    }

    override fun close() {
        try {
            raf?.close()
        } catch (_: Exception) {
            // Ignore.
        }
        raf = null
        header = null
        key = null
        position = 0L
        currentIndex = -1L
        currentChunk = ByteArray(0)
        sourceUri = null
    }

    class Factory(private val repository: VaultRepository) : DataSource.Factory {
        override fun createDataSource(): DataSource = VaultDataSource(repository)
    }
}
