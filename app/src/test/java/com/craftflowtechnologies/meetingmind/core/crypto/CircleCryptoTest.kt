package com.craftflowtechnologies.meetingmind.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CircleCryptoTest {

    @Test
    fun `generateKey produces valid 256-bit AES key`() {
        val key1 = CircleCrypto.generateKey()
        val key2 = CircleCrypto.generateKey()
        assertNotNull(key1)
        assertNotNull(key2)
        assertNotEquals(key1, key2)
        assertTrue(key1.length >= 40)
    }

    @Test
    fun `encrypt and decrypt round trip recovers plaintext`() {
        val key = CircleCrypto.generateKey()
        val original = "Please pray for my mother recovering from surgery this week."

        val encrypted = CircleCrypto.encrypt(original, key)
        assertNotEquals(original, encrypted)

        val decrypted = CircleCrypto.decrypt(encrypted, key)
        assertEquals(original, decrypted)
    }

    @Test
    fun `different keys cannot decrypt each others ciphertext`() {
        val key1 = CircleCrypto.generateKey()
        val key2 = CircleCrypto.generateKey()

        val encrypted = CircleCrypto.encrypt("Secret prayer request", key1)
        val failed = runCatching { CircleCrypto.decrypt(encrypted, key2) }.isFailure
        assertTrue(failed)
    }

    @Test
    fun `build and parse invite URI successfully exchanges room key`() {
        val key = CircleCrypto.generateKey()
        val circleId = "circle-123"
        val circleName = "Men's Fellowship"

        val inviteUri = CircleCrypto.buildInviteUri(circleId, circleName, key)
        assertTrue(inviteUri.startsWith("mindcircle://join?"))

        val parsed = CircleCrypto.parseInviteUri(inviteUri)
        assertNotNull(parsed)
        assertEquals(circleId, parsed?.circleId)
        assertEquals(circleName, parsed?.circleName)
        assertEquals(key, parsed?.encryptionKeyBase64)
    }
}
