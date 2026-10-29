package com.example

import com.example.ui.components.FileManagerCategory
import com.example.ui.components.splitContentIntoSections
import org.junit.Assert.*
import org.junit.Test

class ProjectFeaturesUnitTest {

    @Test
    fun testFileManagerCategories() {
        assertEquals("Java File", FileManagerCategory.JAVA.label)
        assertEquals("app/src/main/java", FileManagerCategory.JAVA.folderPath)
        assertEquals("Java", FileManagerCategory.JAVA.defaultTitle)

        assertEquals("Resources File", FileManagerCategory.RESOURCES.label)
        assertEquals("app/src/main/res", FileManagerCategory.RESOURCES.folderPath)

        assertEquals("Assets File", FileManagerCategory.ASSETS.label)
        assertEquals("app/src/main/assets", FileManagerCategory.ASSETS.folderPath)

        assertEquals("Lib File", FileManagerCategory.LIB.label)
        assertEquals("app/libs", FileManagerCategory.LIB.folderPath)

        assertEquals("Jni File", FileManagerCategory.JNI.label)
        assertEquals("app/src/main/jni", FileManagerCategory.JNI.folderPath)
    }

    @Test
    fun testMarkdownSplitContentIntoSections() {
        val markdown = """
            Here is your code:
            ```java
            public class MainActivity extends AppCompatActivity {
                // code here
            }
            ```
            Make sure to register it in AndroidManifest.xml.
        """.trimIndent()

        val sections = splitContentIntoSections(markdown)
        assertEquals(3, sections.size)

        assertFalse(sections[0].isCode)
        assertTrue(sections[0].text.contains("Here is your code"))

        assertTrue(sections[1].isCode)
        assertEquals("java", sections[1].language)
        assertTrue(sections[1].text.contains("MainActivity extends AppCompatActivity"))

        assertFalse(sections[2].isCode)
        assertTrue(sections[2].text.contains("AndroidManifest.xml"))
    }

    @Test
    fun testMarkdownWithoutCodeBlocks() {
        val plainText = "Hello! How can I help you today?"
        val sections = splitContentIntoSections(plainText)
        assertEquals(1, sections.size)
        assertFalse(sections[0].isCode)
        assertEquals("Hello! How can I help you today?", sections[0].text)
    }
}
