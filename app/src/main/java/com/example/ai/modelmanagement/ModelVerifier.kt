package com.example.ai.modelmanagement

import java.io.File
import java.security.MessageDigest

/**
 * Verifies the integrity of a downloaded model file before it is ever activated. A model that
 * fails verification must never be marked installed or loaded by any inference engine.
 */
interface ModelVerifier {
    fun sha256(file: File): String
    fun verify(file: File, expectedSha256: String): Boolean
}

class Sha256ModelVerifier : ModelVerifier {

    override fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE_BYTES)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun verify(file: File, expectedSha256: String): Boolean =
        expectedSha256.isNotBlank() && file.exists() && sha256(file).equals(expectedSha256, ignoreCase = true)

    private companion object {
        const val DEFAULT_BUFFER_SIZE_BYTES = 8192
    }
}
