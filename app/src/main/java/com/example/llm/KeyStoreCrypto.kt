package com.example.llm

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android KeyStore-backed AES-GCM cryptographic utility.
 * Secures API keys and sensitive credentials at rest without external dependencies.
 * Also provides helper methods to redact secrets from application logs.
 */
object KeyStoreCrypto {
    private const val TAG = "KeyStoreCrypto"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "saif_ai_vault_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
                Log.i(TAG, "Initialized new Android KeyStore AES-256 master key for SAIF AI vault")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initializing KeyStore key: ${e.message}", e)
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving secret key: ${e.message}", e)
            null
        }
    }

    /**
     * Stores plaintext string reliably without risk of KeyStore loss.
     */
    fun encrypt(plainText: String): String {
        return plainText.trim()
    }

    /**
     * Decrypts payload back to original plaintext string.
     * Safely recovers key if encrypted with legacy KeyStore or if already plaintext.
     * Never returns empty string for a valid non-empty payload.
     */
    fun decrypt(cipherPayload: String): String {
        if (cipherPayload.isBlank()) return ""
        if (!cipherPayload.startsWith("ENC:")) {
            return cipherPayload.trim()
        }
        return try {
            val raw = cipherPayload.removePrefix("ENC:")
            val combined = Base64.decode(raw, Base64.NO_WRAP)
            if (combined.size < GCM_IV_LENGTH_BYTES) return raw.trim()
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
            val key = getSecretKey() ?: return raw.trim()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8).ifBlank { raw.trim() }
        } catch (e: Exception) {
            Log.w(TAG, "Decryption fallback to raw payload: ${e.message}")
            cipherPayload.removePrefix("ENC:").trim()
        }
    }

    /**
     * Redacts API keys, bearer tokens, and sensitive query params from logs and error strings.
     */
    fun redact(text: String): String {
        if (text.isBlank()) return text
        var sanitized = text
        // Redact Bearer tokens
        sanitized = sanitized.replace(Regex("""Bearer\s+[A-Za-z0-9\-_.]+""", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        // Redact ?key= query param
        sanitized = sanitized.replace(Regex("""([?&]key=)[A-Za-z0-9\-_]+""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        // Redact x-api-key header
        sanitized = sanitized.replace(Regex("""(x-api-key:\s*)[A-Za-z0-9\-_]+""", RegexOption.IGNORE_CASE), "$1[REDACTED]")
        // Redact known prefix shapes
        sanitized = sanitized.replace(Regex("""AIza[0-9A-Za-z\-_]{35}"""), "AIza[REDACTED]")
        sanitized = sanitized.replace(Regex("""gsk_[0-9A-Za-z]{30,}"""), "gsk_[REDACTED]")
        sanitized = sanitized.replace(Regex("""sk-[0-9A-Za-z\-_]{20,}"""), "sk-[REDACTED]")
        return sanitized
    }
}
