package com.example.gemini.data.local

import java.io.File
import java.security.MessageDigest

/** Integrity pin for the fixed upstream Ubuntu rootfs. Not used for user-selected archives. */
internal object RootfsIntegrity {
    // SHA-256 published by the GitHub release asset metadata for the exact pinned URL.
    const val UBUNTU_ARM64_SHA256 = "5ab35b90cd9a9f180656261ba400a135c4c01c2da4b74522118342f985c2d328"
    const val UBUNTU_ARM64_SIZE = 57831960L

    fun matches(file: File, expectedSize: Long, expectedSha256: String): Boolean {
        if (!file.isFile || file.length() != expectedSize || !expectedSha256.matches(Regex("[0-9a-f]{64}"))) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return MessageDigest.isEqual(actual.toByteArray(Charsets.US_ASCII), expectedSha256.toByteArray(Charsets.US_ASCII))
    }
}
