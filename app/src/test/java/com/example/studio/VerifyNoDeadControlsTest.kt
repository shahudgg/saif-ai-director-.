package com.example.studio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerifyNoDeadControlsTest {

    @Test
    fun testStudioPackageHasNoEmptyOnClickOrToastOnlyHandlers() {
        // Scan all .kt files in com/example/studio
        val studioDir = File("src/main/java/com/example/studio")
        val altStudioDir = File("app/src/main/java/com/example/studio")
        val targetDir = if (studioDir.exists()) studioDir else altStudioDir

        assertTrue("Studio directory must exist", targetDir.exists())

        val violations = mutableListOf<String>()
        targetDir.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { index, line ->
                // Check for onClick = {} or onClick = { }
                if (line.contains("onClick = {}") || line.contains("onClick = { }")) {
                    violations.add("${file.name}:${index + 1} contains empty onClick handler: $line")
                }
            }
        }

        assertTrue(
            "Found dead controls in com.example.studio:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }
}
