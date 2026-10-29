package com.example

import com.example.builder.ProjectDiskBridge
import com.example.builder.ProjectFileEntry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests verifying the prompt-to-disk autonomous code parsing,
 * JSON handling, and clean decoupled pipeline without silent mock fallbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutonomousCoderPipelineTest {

    @Test
    fun testParseJsonArrayFormat() {
        val jsonOutput = """
            [
              {
                "path": "app/src/main/res/layout/activity_main.xml",
                "content": "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"><TextView android:id=\"@+id/tv_title\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:text=\"Expense Manager\" /></LinearLayout>"
              },
              {
                "path": "app/src/main/java/com/saifai/app/MainActivity.java",
                "content": "package com.saifai.app;\n\nimport android.os.Bundle;\nimport androidx.appcompat.app.AppCompatActivity;\n\npublic class MainActivity extends AppCompatActivity {\n    @Override\n    protected void onCreate(Bundle savedInstanceState) {\n        super.onCreate(savedInstanceState);\n        setContentView(R.layout.activity_main);\n    }\n}"
              }
            ]
        """.trimIndent()

        val parsed = ProjectDiskBridge.parseGeneratedFiles(jsonOutput)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.path.contains("activity_main.xml") })
        assertTrue(parsed.any { it.path.contains("MainActivity.java") })
    }

    @Test
    fun testParseMarkdownFencedJson() {
        val fencedJson = """
            ```json
            [
              {
                "path": "app/src/main/res/layout/activity_main.xml",
                "content": "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"></FrameLayout>"
              },
              {
                "path": "app/src/main/java/com/example/MainActivity.kt",
                "content": "package com.example\n\nimport android.os.Bundle\nimport androidx.appcompat.app.AppCompatActivity\n\nclass MainActivity : AppCompatActivity()"
              }
            ]
            ```
        """.trimIndent()

        val parsed = ProjectDiskBridge.parseGeneratedFiles(fencedJson)
        assertEquals(2, parsed.size)
        assertEquals("app/src/main/res/layout/activity_main.xml", parsed[0].path)
        assertEquals("app/src/main/java/com/example/MainActivity.kt", parsed[1].path)
    }

    @Test
    fun testParseWrappedJsonObjectWithFilesArray() {
        val jsonWrapper = """
            {
              "files": [
                {
                  "path": "app/src/main/AndroidManifest.xml",
                  "content": "<manifest package=\"com.test\"></manifest>"
                },
                {
                  "path": "app/src/main/res/values/strings.xml",
                  "content": "<resources><string name=\"app_name\">Habit Tracker</string></resources>"
                }
              ]
            }
        """.trimIndent()

        val parsed = ProjectDiskBridge.parseGeneratedFiles(jsonWrapper)
        assertEquals(2, parsed.size)
        assertTrue(parsed.any { it.path.endsWith("AndroidManifest.xml") })
        assertTrue(parsed.any { it.path.endsWith("strings.xml") })
    }

    @Test
    fun testEmptyOrInvalidResponseReturnsEmptyListForExplicitErrorHandling() {
        val emptyParsed = ProjectDiskBridge.parseGeneratedFiles("")
        assertTrue("Empty output must return empty list to trigger honest error UI", emptyParsed.isEmpty())

        val invalidParsed = ProjectDiskBridge.parseGeneratedFiles("Sorry, I cannot help with this query.")
        assertTrue("Non-code output must return empty list so UI shows red error", invalidParsed.isEmpty())
    }
}
