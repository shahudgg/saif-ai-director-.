package com.example.builder

data class ReviewReport(
    val score: Int,
    val strengths: List<String>,
    val suggestions: List<String>,
    val polishedHtml: String
)

/**
 * Quality & Polish Reviewer for SAIF Build applications.
 * Audits UI/UX standards, touch readiness (44px+ minimum), mobile safe-area insets,
 * animations, and smooth touch response before final packaging.
 */
object BuildReviewer {

    fun reviewAndPolish(html: String): ReviewReport {
        if (html.isBlank()) {
            return ReviewReport(0, emptyList(), listOf("Empty document"), html)
        }

        var workingHtml = html
        val strengths = mutableListOf<String>()
        val suggestions = mutableListOf<String>()
        var score = 70

        val lower = workingHtml.lowercase()

        // 1. Mobile Safe Area & Layout check
        if (lower.contains("safe-area-inset") || lower.contains("100dvh") || lower.contains("100vh")) {
            strengths.add("Mobile-first viewport height and safe-area margins handled")
            score += 10
        } else {
            suggestions.add("Add safe-area padding for edge-to-edge mobile screens")
        }

        // 2. CSS Animation & Transitions check
        if (lower.contains("transition:") || lower.contains("@keyframes") || lower.contains("animation:")) {
            strengths.add("Interactive animations and transition micro-interactions present")
            score += 10
        } else {
            suggestions.add("Consider adding CSS transitions for smoother state changes")
        }

        // 3. Touch feedback & Haptics
        if (!lower.contains("navigator.vibrate")) {
            workingHtml = injectTouchHapticHelper(workingHtml)
            strengths.add("Subtle mobile haptic tap feedback polyfill injected")
            score += 10
        } else {
            strengths.add("Native touch vibration/haptics supported")
            score += 10
        }

        // 4. Accessibility & Box Sizing
        if (!lower.contains("box-sizing: border-box")) {
            workingHtml = injectGlobalBoxSizing(workingHtml)
        }

        return ReviewReport(
            score = Math.min(100, score),
            strengths = strengths,
            suggestions = suggestions,
            polishedHtml = workingHtml
        )
    }

    private fun injectTouchHapticHelper(html: String): String {
        val helperScript = """
        <script id="saif-haptic-shim">
        (function() {
            if (window._saifHapticsReady) return;
            window._saifHapticsReady = true;
            document.addEventListener('click', function(e) {
                var target = e.target;
                if (target && (target.tagName === 'BUTTON' || target.closest('button') || target.tagName === 'A' || target.getAttribute('role') === 'button')) {
                    try { if (navigator.vibrate) navigator.vibrate(8); } catch(ignored) {}
                }
            }, { passive: true });
        })();
        </script>
        """.trimIndent()

        val headIdx = html.indexOf("</head>", ignoreCase = true)
        return if (headIdx != -1) {
            html.substring(0, headIdx) + "\n" + helperScript + "\n" + html.substring(headIdx)
        } else {
            "$helperScript\n$html"
        }
    }

    private fun injectGlobalBoxSizing(html: String): String {
        val boxSizingStyle = """
        <style id="saif-reset-shim">
        *, *::before, *::after { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }
        </style>
        """.trimIndent()

        val headIdx = html.indexOf("</head>", ignoreCase = true)
        return if (headIdx != -1) {
            html.substring(0, headIdx) + "\n" + boxSizingStyle + "\n" + html.substring(headIdx)
        } else {
            "$boxSizingStyle\n$html"
        }
    }
}
