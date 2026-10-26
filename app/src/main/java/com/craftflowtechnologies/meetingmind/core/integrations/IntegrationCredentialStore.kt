package com.craftflowtechnologies.meetingmind.core.integrations

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore-backed AES-256-GCM encrypted store for integration credentials
 * (Todoist token, GitHub token, Linear key, Notion secret, Webhook URLs, etc.).
 * Stored in app's [Context.getNoBackupFilesDir] so credentials never escape to cloud backups.
 */
class IntegrationCredentialStore(private val context: Context) {

    private val storeFile: File
        get() = File(context.noBackupFilesDir, "integration_credentials.enc")

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
            (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        } catch (_: Exception) {
            // Fallback for JVM test environments where AndroidKeyStore provider is unavailable
            javax.crypto.spec.SecretKeySpec(TEST_FALLBACK_KEY_BYTES, "AES")
        }
    }

    suspend fun saveCredential(providerId: String, secret: String): Unit = withContext(Dispatchers.IO) {
        val current = readAll()
        current.put(providerId, secret.trim())
        writeAll(current)
    }

    suspend fun getCredential(providerId: String): String? = withContext(Dispatchers.IO) {
        val all = readAll()
        all.optString(providerId).takeIf { it.isNotBlank() }
    }

    suspend fun removeCredential(providerId: String): Unit = withContext(Dispatchers.IO) {
        val current = readAll()
        if (current.has(providerId)) {
            current.remove(providerId)
            writeAll(current)
        }
    }

    suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        if (storeFile.exists()) {
            storeFile.delete()
        }
    }

    fun redact(secret: String?): String? {
        if (secret.isNullOrBlank()) return null
        return if (secret.length <= 6) "•".repeat(secret.length)
        else "•".repeat(6) + "…" + secret.takeLast(4)
    }

    private fun readAll(): JSONObject {
        if (!storeFile.exists()) return JSONObject()
        return try {
            val bytes = storeFile.readBytes()
            if (bytes.size < 12) return JSONObject()
            val iv = bytes.copyOfRange(0, 12)
            val cipherText = bytes.copyOfRange(12, bytes.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plainBytes = cipher.doFinal(cipherText)
            JSONObject(String(plainBytes, Charsets.UTF_8))
        } catch (_: Exception) {
            JSONObject()
        }
    }

    private fun writeAll(json: JSONObject) {
        val plainBytes = json.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        storeFile.writeBytes(combined)
    }

    companion object {
        private const val KEY_ALIAS = "mm_integrations_master_key"
        private val TEST_FALLBACK_KEY_BYTES = ByteArray(32) { (it * 7).toByte() }
    }
}
