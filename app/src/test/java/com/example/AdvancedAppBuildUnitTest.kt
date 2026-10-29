package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.builder.AutonomousAppBuilder
import com.example.compiler.AppCompilerEngine
import com.example.compiler.AppHtmlSynthesizer
import com.example.ui.components.ProjectData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Rigorous Unit Test verifying that apps build and validate end-to-end
 * across HTML extraction, syntax verification, HTML synthesis, and real APK packaging.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvancedAppBuildUnitTest {

    @Test
    fun testExtractHtmlDocumentSuccess() {
        val rawResponse = """
            Here is your app:
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Calculator</title>
            </head>
            <body>
                <div id="calc">Calculator App</div>
            </body>
            </html>
            Enjoy the app!
        """.trimIndent()

        val extracted = AutonomousAppBuilder.extractHtmlDocument(rawResponse)
        assertNotNull("Extracted HTML should not be null", extracted)
        assertTrue("Extracted HTML should start with <!DOCTYPE", extracted!!.startsWith("<!DOCTYPE html", ignoreCase = true))
        assertTrue("Extracted HTML should end with </html>", extracted.endsWith("</html>", ignoreCase = true))
        assertFalse("Extracted HTML should not contain outside markdown/commentary", extracted.contains("Here is your app"))
    }

    @Test
    fun testExtractHtmlDocumentTruncationDetection() {
        val truncatedResponse = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Incomplete</title></head>
            <body>
                <div class="container">
        """.trimIndent()

        val extracted = AutonomousAppBuilder.extractHtmlDocument(truncatedResponse)
        assertNull("Truncated response missing </html> should return null", extracted)
    }

    @Test
    fun testHealTruncatedHtmlClosesUnclosedScriptAndTags() {
        val truncatedCode = """
            <!DOCTYPE html>
            <html lang="en">
            <head><title>Stopwatch</title></head>
            <body>
                <div id="display">00:00</div>
                <script>
                    let time = 0;
                    console.log("Starting timer");
        """.trimIndent()

        val healed = AutonomousAppBuilder.healTruncatedHtml(truncatedCode)
        assertNotNull("Healed HTML should not be null", healed)
        assertTrue("Healed HTML must close script tag", healed!!.contains("</script>"))
        assertTrue("Healed HTML must close body tag", healed.contains("</body>"))
        assertTrue("Healed HTML must end with </html>", healed.endsWith("</html>"))
    }

    @Test
    fun testAppHtmlSynthesizerReturnsExistingHtml() {
        val project = ProjectData(
            appName = "Calculator",
            packageName = "com.example.calculator",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_proj_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val sampleHtml = "<!DOCTYPE html><html><head><!-- saif-ai-build --></head><body><h1>Working Calculator</h1></body></html>"
            File(tempDir, "index.html").writeText(sampleHtml)

            val synthesized = AppHtmlSynthesizer.synthesize(project, tempDir)
            assertEquals("Synthesized HTML should match existing index.html", sampleHtml, synthesized)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testAppHtmlSynthesizerThrowsWhenNoHtml() {
        val project = ProjectData(
            appName = "EmptyApp",
            packageName = "com.example.emptyapp",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_empty_", "").apply {
            delete()
            mkdirs()
        }
        try {
            AppHtmlSynthesizer.synthesize(project, tempDir)
            fail("Should have thrown IllegalStateException when index.html does not exist")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("Build the app first") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testRJavaGeneratorOutput() {
        val project = ProjectData(
            appName = "SmartApp",
            packageName = "com.test.smartapp",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )
        val tempDir = File.createTempFile("test_rjava_", "").apply {
            delete()
            mkdirs()
        }
        try {
            val resDir = File(tempDir, "app/src/main/res/layout").apply { mkdirs() }
            val layoutFile = File(resDir, "main.xml")
            layoutFile.writeText("""
                <?xml version="1.0" encoding="utf-8"?>
                <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/main_container"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent">
                    <Button
                        android:id="@+id/btn_action"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Click Me" />
                </LinearLayout>
            """.trimIndent())

            val rJava = AppCompilerEngine.generateRJavaSource(project, tempDir)
            assertTrue(rJava.contains("package com.test.smartapp;"))
            assertTrue(rJava.contains("public final class R {"))
            assertTrue(rJava.contains("public static final class id {"))
            assertTrue(rJava.contains("public static final class layout {"))
            assertTrue(rJava.contains("btn_action"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun testBaseRunnerAssetExistsAndHasValidStructure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull("Context must be valid for standalone compiler", context)
    }

    @Test
    fun testEndToEndApkCompilationPipeline() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val project = ProjectData(
            appName = "Calculator",
            packageName = "com.saifai.calculator",
            minSdk = 24,
            targetSdk = 34,
            buildStudio = "Build Studio",
            language = "Java",
            iconBitmap = null
        )

        val sanitizedAppName = "Calculator"
        val projectDir = File(context.filesDir, "projects/$sanitizedAppName").apply { mkdirs() }
        File(projectDir, "index.html").writeText("<!DOCTYPE html><html><head><!-- saif-ai-build --></head><body><h1>Calculator App</h1></body></html>")

        val result = AppCompilerEngine.compileProject(
            context = context,
            project = project,
            onStepChanged = { _, _ -> },
            onLogAdded = { }
        )

        assertTrue("Build must succeed: ${(result as? com.example.compiler.BuildResult.Error)?.message}", result is com.example.compiler.BuildResult.Success)
        val success = result as com.example.compiler.BuildResult.Success
        assertTrue("Output APK file must exist", success.apkFile.exists())
        assertTrue("Output APK size must be > 3KB", success.apkFile.length() > 3000)

        val entries = mutableListOf<String>()
        java.util.zip.ZipFile(success.apkFile).use { zip ->
            val it = zip.entries()
            while (it.hasMoreElements()) {
                entries.add(it.nextElement().name)
            }
        }
        assertTrue("Must contain AndroidManifest.xml", entries.contains("AndroidManifest.xml"))
        assertTrue("Must contain classes.dex", entries.contains("classes.dex"))
        assertTrue("Must contain resources.arsc", entries.contains("resources.arsc"))
        assertTrue("Must contain assets/index.html", entries.contains("assets/index.html"))
        assertTrue("Must contain launcher icon", entries.contains("res/drawable/ic_launcher.png"))
    }
}
