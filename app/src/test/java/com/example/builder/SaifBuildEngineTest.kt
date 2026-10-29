package com.example.builder

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SaifBuildEngineTest {

    @Test
    fun testHtmlGuardDoctypeAndViewportInjection() {
        val rawHtml = """
            <html>
            <head><title>Test App</title></head>
            <body><h1>Hello</h1></body>
            </html>
        """.trimIndent()

        val report = HtmlGuard.inspect(rawHtml)
        assertTrue(report.sanitizedHtml.startsWith("<!DOCTYPE html>"))
        assertTrue(report.sanitizedHtml.contains("name=\"viewport\""))
        assertTrue(report.sanitizedHtml.contains("charset=\"UTF-8\""))
        assertTrue(report.sanitizedHtml.contains("<!-- saif-ai-build -->"))
    }

    @Test
    fun testHtmlGuardUnclosedTagHealing() {
        val brokenHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Unclosed</title></head>
            <body>
                <div>Content without closing div
                <script>
                    console.log("active");
            </html>
        """.trimIndent()

        val report = HtmlGuard.inspect(brokenHtml)
        // Check that unclosed script was healed
        assertTrue(report.sanitizedHtml.contains("</script>"))
        assertTrue(report.sanitizedHtml.contains("</body>"))
    }

    @Test
    fun testHtmlGuardBlockingDialogPolyfill() {
        val alertHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Alert App</title></head>
            <body>
                <button onclick="alert('Hello world')">Click Me</button>
            </body>
            </html>
        """.trimIndent()

        val report = HtmlGuard.inspect(alertHtml)
        // Should contain non-blocking dialog shim
        assertTrue(report.sanitizedHtml.contains("saif-dialog-shim"))
    }

    @Test
    fun testHtmlGuardJsBracketMatching() {
        val brokenJsHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Bracket Test</title></head>
            <body>
                <script>
                    function test() {
                        if (true) {
                            console.log("missing closing brace");
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        val report = HtmlGuard.inspect(brokenJsHtml)
        val bracketErrors = report.errors.filter { it.rule == "RULE_JS_BRACKETS" }
        assertEquals(1, bracketErrors.size)
    }

    @Test
    fun testSandboxRunnerCatchesMissingElementIds() {
        val htmlWithMissingId = """
            <!DOCTYPE html>
            <html>
            <head><title>ID Test</title></head>
            <body>
                <button id="real-button">Action</button>
                <script>
                    document.getElementById("ghost-button").addEventListener("click", function() {});
                </script>
            </body>
            </html>
        """.trimIndent()

        val verification = SandboxRunner.verifyDomAndJsBindings(htmlWithMissingId)
        assertTrue("Must detect missing ID ghost-button", verification.errors.any { it.contains("ghost-button") })
    }

    @Test
    fun testBuildReviewerPolishesMobileHapticsAndBoxSizing() {
        val basicHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Polished App</title></head>
            <body>
                <button>Tap</button>
            </body>
            </html>
        """.trimIndent()

        val report = BuildReviewer.reviewAndPolish(basicHtml)
        assertTrue(report.score >= 70)
        assertTrue(report.polishedHtml.contains("box-sizing: border-box"))
        assertTrue(report.polishedHtml.contains("saif-haptic-shim"))
    }
}
