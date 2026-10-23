package com.vidplay.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vidplay.app.data.vault.VaultCrypto
import com.vidplay.app.data.vault.VaultKeyManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VaultKeyManagerTest {

    @Test
    fun keystoreAndPinCanUnwrapTheSameDek() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "vault-key-test-${UUID.randomUUID()}")
        dir.mkdirs()
        val manager = VaultKeyManager(dir)
        val dek = VaultCrypto.randomKey()
        try {
            manager.create(dek, "test-pin-1234")
            assertTrue(manager.hasVault())

            val fromKeystore = manager.unwrapWithKeystore()
            assertNotNull(fromKeystore)
            assertArrayEquals(dek.encoded, fromKeystore!!.encoded)

            val fromPin = manager.unwrapWithPin("test-pin-1234")
            assertNotNull(fromPin)
            assertArrayEquals(dek.encoded, fromPin!!.encoded)

            assertNull(manager.unwrapWithPin("wrong-pin-1234"))
        } finally {
            manager.deleteKeys()
            dir.deleteRecursively()
        }
    }
}
