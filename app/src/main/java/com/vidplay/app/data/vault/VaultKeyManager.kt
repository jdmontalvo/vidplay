package com.vidplay.app.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Two-layer key storage:
 *  - A random 256-bit data encryption key (DEK) encrypts the vault files.
 *  - The DEK is wrapped twice: once by a non-exportable Android Keystore key
 *    (TEE backed when available) and once by a key derived from the recovery PIN.
 *
 * The biometric prompt is the UI gate for the Keystore-wrapped copy. The PIN copy
 * is the recovery path if the device lock/biometrics change.
 */
class VaultKeyManager(
    private val vaultDir: File,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) {
    private val keystoreFile = File(vaultDir, ".dek")
    private val pinFile = File(vaultDir, ".pin")
    private val random = SecureRandom()

    fun hasVault(): Boolean = keystoreFile.exists() && pinFile.exists()

    @Synchronized
    fun create(dek: SecretKey, pin: String) {
        if (pin.length < 8) throw IllegalArgumentException("El PIN debe tener al menos 8 caracteres")
        if (hasVault()) throw IllegalStateException("El baúl ya está configurado")
        vaultDir.mkdirs()
        keystoreFile.writeBytes(wrapWithKeystore(dek))
        pinFile.writeBytes(wrapWithPin(dek, pin))
    }

    @Synchronized
    fun unwrapWithKeystore(): SecretKey? {
        if (!keystoreFile.exists()) return null
        return try {
            val wrapped = keystoreFile.readBytes()
            if (wrapped.size <= VaultCrypto.IV_SIZE) return null
            val iv = wrapped.copyOfRange(0, VaultCrypto.IV_SIZE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKeystoreKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(wrapped, VaultCrypto.IV_SIZE, wrapped.size - VaultCrypto.IV_SIZE)
            SecretKeySpec(plain, "AES")
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    fun unwrapWithPin(pin: String): SecretKey? {
        if (!pinFile.exists()) return null
        return try {
            val blob = pinFile.readBytes()
            if (blob.size <= 16 + VaultCrypto.IV_SIZE) return null
            val salt = blob.copyOfRange(0, 16)
            val iv = blob.copyOfRange(16, 16 + VaultCrypto.IV_SIZE)
            val ciphertext = blob.copyOfRange(16 + VaultCrypto.IV_SIZE, blob.size)
            val key = derivePinKey(pin, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            SecretKeySpec(cipher.doFinal(ciphertext), "AES")
        } catch (_: Exception) {
            null
        }
    }

    fun deleteKeys() {
        keystoreFile.delete()
        pinFile.delete()
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)
        } catch (_: Exception) {
            // Best effort.
        }
    }

    private fun wrapWithKeystore(dek: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKeystoreKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(dek.encoded)
        return iv + encrypted
    }

    private fun wrapWithPin(dek: SecretKey, pin: String): ByteArray {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val key = derivePinKey(pin, salt)
        val iv = ByteArray(VaultCrypto.IV_SIZE)
        random.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(dek.encoded)
        return salt + iv + encrypted
    }

    private fun derivePinKey(pin: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    companion object {
        private const val DEFAULT_KEY_ALIAS = "vidplay_vault_kek_v1"
        private const val PBKDF2_ITERATIONS = 210_000
    }
}

class VaultSession {
    @Volatile
    private var key: SecretKey? = null

    @Volatile
    var playbackActive: Boolean = false

    @Volatile
    var lastActivity: Long = System.currentTimeMillis()

    fun unlock(dek: SecretKey) {
        key = dek
        lastActivity = System.currentTimeMillis()
    }

    fun lock() {
        key = null
    }

    fun isUnlocked(): Boolean = key != null

    fun keyOrNull(): SecretKey? = key

    fun requireKey(): SecretKey = key ?: throw VaultLockedException()

    fun touch() {
        lastActivity = System.currentTimeMillis()
    }
}

class VaultLockedException : IllegalStateException("El baúl está bloqueado")
