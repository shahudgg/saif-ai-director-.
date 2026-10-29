package com.example.studio.director

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Real at-rest encryption (AES-256-GCM, key held in the Android Keystore) for secrets the Director stores.
 * If the Keystore is unavailable, [seal] returns null and the caller must NOT store the secret in plaintext.
 */
object SecretBox {
    private const val ALIAS = "saif_director_secret_key"
    private const val PREFIX = "ENC2:"

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
            generateKey()
        }
    } catch (_: Exception) { null }

    fun seal(plain: String): String? {
        if (plain.isBlank()) return ""
        val k = key() ?: return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
            PREFIX + Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        } catch (_: Exception) { null }
    }

    fun open(sealed: String): String? {
        if (!sealed.startsWith(PREFIX)) return null
        val k = key() ?: return null
        return try {
            val all = Base64.decode(sealed.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, all.copyOfRange(0, 12))) }
            String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
        } catch (_: Exception) { null }
    }
}
