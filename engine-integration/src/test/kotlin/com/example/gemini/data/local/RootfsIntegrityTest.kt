package com.example.gemini.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RootfsIntegrityTest {
    @Test fun validatesSizeAndExactDigest() {
        val file = Files.createTempFile("saif-digest-test", ".bin").toFile()
        try {
            file.writeText("abc")
            val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            assertTrue(RootfsIntegrity.matches(file, 3, abc))
            assertFalse(RootfsIntegrity.matches(file, 4, abc))
            assertFalse(RootfsIntegrity.matches(file, 3, "0".repeat(64)))
            assertFalse(RootfsIntegrity.matches(file, 3, "invalid"))
            file.writeText("abd")
            assertFalse(RootfsIntegrity.matches(file, 3, abc))
        } finally { file.delete() }
    }
}
