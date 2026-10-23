package com.vidplay.app

import com.vidplay.app.data.vault.VaultCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.crypto.AEADBadTagException

class VaultCryptoTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val key = VaultCrypto.randomKey()

    @Test
    fun roundTripSmallFile() = runBlocking {
        val plain = "VidPlay vault test".toByteArray()
        val file = temp.newFile("small.vpv")
        val written = VaultCrypto.encrypt(ByteArrayInputStream(plain), file, key)
        assertEquals(plain.size.toLong(), written.plainSize)
        assertEquals(VaultCrypto.expectedEncryptedSize(plain.size.toLong()), file.length())

        val output = ByteArrayOutputStream()
        VaultCrypto.decrypt(file, output, key)
        assertArrayEquals(plain, output.toByteArray())
    }

    @Test
    fun roundTripAcrossChunks() = runBlocking {
        val plain = ByteArray(VaultCrypto.CHUNK_SIZE * 2 + 12345) { (it * 31).toByte() }
        val file = temp.newFile("chunks.vpv")
        var lastProgress = 0L
        VaultCrypto.encrypt(ByteArrayInputStream(plain), file, key) { lastProgress = it }
        assertEquals(plain.size.toLong(), lastProgress)
        assertEquals(VaultCrypto.expectedEncryptedSize(plain.size.toLong()), file.length())

        val output = ByteArrayOutputStream()
        var finalDone = 0L
        VaultCrypto.decrypt(file, output, key) { done, _ -> finalDone = done }
        assertArrayEquals(plain, output.toByteArray())
        assertEquals(plain.size.toLong(), finalDone)
    }

    @Test
    fun emptyFileRoundTrip() = runBlocking {
        val file = temp.newFile("empty.vpv")
        val header = VaultCrypto.encrypt(ByteArrayInputStream(ByteArray(0)), file, key)
        assertEquals(0L, header.plainSize)
        assertEquals(VaultCrypto.HEADER_SIZE.toLong(), file.length())
        val output = ByteArrayOutputStream()
        VaultCrypto.decrypt(file, output, key)
        assertEquals(0, output.size())
    }

    @Test
    fun headerContainsPlainSizeAndHash() = runBlocking {
        val plain = ByteArray(4096) { it.toByte() }
        val file = temp.newFile("header.vpv")
        VaultCrypto.encrypt(ByteArrayInputStream(plain), file, key)
        val header = VaultCrypto.readHeader(file)
        assertEquals(plain.size.toLong(), header.plainSize)
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(plain),
            header.sha256,
        )
    }

    @Test
    fun tamperedChunkFailsAuthentication() = runBlocking {
        val plain = ByteArray(2048) { it.toByte() }
        val file = temp.newFile("tampered.vpv")
        VaultCrypto.encrypt(ByteArrayInputStream(plain), file, key)
        RandomAccessFile(file, "rw").use { raf ->
            val offset = VaultCrypto.HEADER_SIZE + VaultCrypto.IV_SIZE + 100L
            raf.seek(offset)
            val original = raf.readByte()
            raf.seek(offset)
            raf.writeByte(original.toInt() xor 0x01)
        }
        assertThrows(AEADBadTagException::class.java) {
            runBlocking {
                VaultCrypto.decrypt(file, ByteArrayOutputStream(), key)
            }
        }
        Unit
    }

    @Test
    fun tamperedHeaderHashFailsVerification() = runBlocking {
        val plain = ByteArray(1024) { it.toByte() }
        val file = temp.newFile("hash.vpv")
        VaultCrypto.encrypt(ByteArrayInputStream(plain), file, key)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(HEADER_SHA_OFFSET.toLong())
            val original = raf.readByte()
            raf.seek(HEADER_SHA_OFFSET.toLong())
            raf.writeByte(original.toInt() xor 0x01)
        }
        assertThrows(SecurityException::class.java) {
            runBlocking {
                VaultCrypto.decrypt(file, ByteArrayOutputStream(), key)
            }
        }
        Unit
    }

    @Test
    fun expectedSizeMath() {
        assertEquals(VaultCrypto.HEADER_SIZE.toLong(), VaultCrypto.expectedEncryptedSize(0))
        assertEquals(
            (VaultCrypto.HEADER_SIZE + 1 + VaultCrypto.IV_SIZE + VaultCrypto.TAG_SIZE).toLong(),
            VaultCrypto.expectedEncryptedSize(1),
        )
        assertEquals(
            (VaultCrypto.HEADER_SIZE + VaultCrypto.CHUNK_SIZE +
                VaultCrypto.IV_SIZE + VaultCrypto.TAG_SIZE).toLong(),
            VaultCrypto.expectedEncryptedSize(VaultCrypto.CHUNK_SIZE.toLong()),
        )
    }

    companion object {
        // magic 8 + version 1 + flags 1 + reserved 2 + chunk size 4 + plain size 8
        private const val HEADER_SHA_OFFSET = 8 + 1 + 1 + 2 + 4 + 8
    }
}
