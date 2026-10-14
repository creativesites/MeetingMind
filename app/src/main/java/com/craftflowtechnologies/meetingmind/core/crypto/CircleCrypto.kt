package com.craftflowtechnologies.meetingmind.core.crypto

import android.util.Base64
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class CircleInvitePayload(
    val circleId: String,
    val circleName: String,
    val encryptionKeyBase64: String
)

/**
 * End-to-end encryption for Private Circles using AES-256-GCM.
 *
 * Each circle possesses a unique 256-bit symmetric key exchanged via invite link or QR code.
 * Relays/servers only ever receive authenticated ciphertext blobs:
 * [IV (12 bytes)] + [Ciphertext + Auth Tag (16 bytes)]
 */
object CircleCrypto {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BYTES = 32 // 256 bits
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val SCHEME = "mindcircle"

    private val secureRandom = SecureRandom()

    /** Generates a fresh, cryptographically secure 256-bit AES key encoded as Base64. */
    fun generateKey(): String {
        val keyBytes = ByteArray(KEY_SIZE_BYTES)
        secureRandom.nextBytes(keyBytes)
        return Base64.encodeToString(keyBytes, Base64.NO_WRAP)
    }

    /**
     * Encrypts UTF-8 [plaintext] using AES-256-GCM.
     * Returns a Base64-encoded string containing [IV (12 bytes) || Ciphertext + Tag].
     */
    fun encrypt(plaintext: String, keyBase64: String): String {
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        require(keyBytes.size == KEY_SIZE_BYTES) { "Invalid AES-256 key size: ${keyBytes.size} bytes" }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val buffer = ByteBuffer.allocate(iv.size + cipherBytes.size)
        buffer.put(iv)
        buffer.put(cipherBytes)

        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    /**
     * Decrypts [ciphertextBase64] using AES-256-GCM.
     * Reconstitutes the original plaintext. Throws on tampering or authentication failure.
     */
    fun decrypt(ciphertextBase64: String, keyBase64: String): String {
        val raw = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        require(raw.size > GCM_IV_LENGTH_BYTES) { "Ciphertext payload is too short" }

        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        require(keyBytes.size == KEY_SIZE_BYTES) { "Invalid AES-256 key size" }

        val iv = raw.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encryptedData = raw.copyOfRange(GCM_IV_LENGTH_BYTES, raw.size)

        val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val decryptedBytes = cipher.doFinal(encryptedData)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /** Builds an invite link for sharing via QR code, messaging apps, or direct link. */
    fun buildInviteUri(circleId: String, circleName: String, keyBase64: String): String {
        val encodedName = URLEncoder.encode(circleName, "UTF-8")
        val encodedKey = URLEncoder.encode(keyBase64, "UTF-8")
        return "$SCHEME://join?id=$circleId&name=$encodedName&key=$encodedKey"
    }

    /**
     * Parses an invite link or raw query string into a [CircleInvitePayload].
     * Accepts both `mindcircle://join?id=...&name=...&key=...` and standard HTTP deep links.
     */
    fun parseInviteUri(uriString: String): CircleInvitePayload? {
        return runCatching {
            val query = when {
                uriString.contains("?") -> uriString.substringAfter("?")
                else -> uriString
            }
            val params = query.split("&").associate { param ->
                val parts = param.split("=", limit = 2)
                val key = parts.getOrNull(0)?.trim().orEmpty()
                val value = parts.getOrNull(1)?.trim().orEmpty()
                key to URLDecoder.decode(value, "UTF-8")
            }

            val id = params["id"]?.takeIf { it.isNotBlank() } ?: return null
            val name = params["name"]?.takeIf { it.isNotBlank() } ?: "Fellowship Circle"
            val key = params["key"]?.takeIf { it.isNotBlank() } ?: return null

            CircleInvitePayload(circleId = id, circleName = name, encryptionKeyBase64 = key)
        }.getOrNull()
    }
}
