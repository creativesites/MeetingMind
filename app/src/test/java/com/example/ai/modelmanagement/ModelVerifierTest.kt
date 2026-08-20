package com.example.ai.modelmanagement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

/** Real SHA-256 computation and verification — no mocking of the class under test. */
class ModelVerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val verifier: ModelVerifier = Sha256ModelVerifier()

    @Test
    fun `sha256 matches a known reference digest`() {
        val file = tempFolder.newFile("known.bin")
        val content = "the quick brown fox jumps over the lazy dog".toByteArray()
        file.writeBytes(content)

        val expected = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
        assertEquals(expected, verifier.sha256(file))
    }

    @Test
    fun `verify succeeds for a correct checksum`() {
        val file = tempFolder.newFile("model.bin")
        file.writeBytes(ByteArray(50_000) { (it % 256).toByte() })
        val correctSha256 = verifier.sha256(file)

        assertTrue(verifier.verify(file, correctSha256))
    }

    @Test
    fun `verify fails for a corrupted file`() {
        val file = tempFolder.newFile("model.bin")
        file.writeBytes(ByteArray(50_000) { (it % 256).toByte() })
        val correctSha256 = verifier.sha256(file)

        // Simulate corruption after the checksum was computed against the original bytes.
        file.appendBytes(byteArrayOf(0x00))

        assertFalse(verifier.verify(file, correctSha256))
    }

    @Test
    fun `verify fails for a missing file`() {
        val missing = tempFolder.root.resolve("does-not-exist.bin")
        assertFalse(verifier.verify(missing, "a".repeat(64)))
    }

    @Test
    fun `verify fails for a blank expected checksum`() {
        val file = tempFolder.newFile("model.bin")
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertFalse(verifier.verify(file, ""))
    }
}
