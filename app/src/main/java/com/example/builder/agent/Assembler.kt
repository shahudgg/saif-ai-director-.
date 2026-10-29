package com.example.builder.agent

import java.io.File

/**
 * Deterministic Assembler that gathers modular source files into a single,
 * self-contained, high-performance HTML5/CSS3/JavaScript document for Android WebView.
 * Preserves the <!-- saif-ai-build --> marker and guarantees 100dvh mobile container support.
 */
object Assembler {

    const val SAIF_BUILD_MARKER = "<!-- saif-ai-build -->"

    /**
     * Assembles all files under [srcDir] into a single self-contained HTML document.
     */
    fun assemble(
        appName: String,
        spec: AppSpec?,
        moduleFiles: Map<String, String>
    ): String {
        val tokensCss = moduleFiles["src/tokens.css"] ?: moduleFiles["tokens.css"] ?: ""
        val baseCss = moduleFiles["src/base.css"] ?: moduleFiles["base.css"] ?: ""
        val componentsCss = moduleFiles["src/components.css"] ?: moduleFiles["components.css"] ?: ""

        val screenHtmls = moduleFiles.filter {
            it.key.startsWith("src/screens/") || it.key.startsWith("screens/")
        }.values.joinToString("\n\n")

        val stateJs = moduleFiles["src/state.js"] ?: moduleFiles["state.js"] ?: defaultStateJs()
        val uiJs = moduleFiles["src/ui.js"] ?: moduleFiles["ui.js"] ?: defaultUiJs()
        val featureJs = moduleFiles.filter {
            it.key.startsWith("src/features/") || it.key.startsWith("features/")
        }.values.joinToString("\n\n")
        val mainJs = moduleFiles["src/main.js"] ?: moduleFiles["main.js"] ?: "document.addEventListener('DOMContentLoaded', () => { if (window.initApp) window.initApp(); });"

        val title = spec?.appName?.ifBlank { appName } ?: appName

        val fontPair = spec?.designSystem?.fontPair ?: "'Inter', system-ui, -apple-system, sans-serif"
        val primaryCol = spec?.designSystem?.primaryColor ?: "#8B5CF6"
        val bgCol = spec?.designSystem?.backgroundColor ?: "#0A0D14"
        val textCol = spec?.designSystem?.textColor ?: "#F1F5F9"
        val surfaceCol = spec?.designSystem?.surfaceColor ?: "#131826"
        val accentCol = spec?.designSystem?.accentColor ?: "#38BDF8"

        val defaultVariables = """
            :root {
                --primary: $primaryCol;
                --accent: $accentCol;
                --bg: $bgCol;
                --surface: $surfaceCol;
                --text: $textCol;
                --text-muted: #94A3B8;
                --border: rgba(255, 255, 255, 0.12);
                --radius-sm: 8px;
                --radius-md: 14px;
                --radius-lg: 20px;
                --shadow-sm: 0 2px 8px rgba(0, 0, 0, 0.25);
                --shadow-md: 0 8px 24px rgba(0, 0, 0, 0.35);
                --font-main: $fontPair;
            }
        """.trimIndent()

        val defaultReset = """
            * {
                box-sizing: border-box;
                margin: 0;
                padding: 0;
                -webkit-tap-highlight-color: transparent;
            }
            html, body {
                width: 100%;
                min-height: 100dvh;
                background-color: var(--bg);
                color: var(--text);
                font-family: var(--font-main);
                overflow-x: hidden;
                overscroll-behavior-y: contain;
                padding-top: env(safe-area-inset-top);
                padding-bottom: env(safe-area-inset-bottom);
                padding-left: env(safe-area-inset-left);
                padding-right: env(safe-area-inset-right);
            }
            button, input, select, textarea {
                font-family: inherit;
                font-size: 1rem;
            }
            button {
                cursor: pointer;
                border: none;
                outline: none;
                min-height: 44px;
                min-width: 44px;
            }
            a { color: inherit; text-decoration: none; }
            @media (prefers-reduced-motion: reduce) {
                *, ::before, ::after {
                    animation-delay: -1ms !important;
                    animation-duration: 1ms !important;
                    animation-iteration-count: 1 !important;
                    background-attachment: initial !important;
                    scroll-behavior: auto !important;
                    transition-duration: 0s !important;
                    transition-delay: 0s !important;
                }
            }
        """.trimIndent()

        val fullHtml = StringBuilder()
        fullHtml.append("<!DOCTYPE html>\n")
        fullHtml.append("<html lang=\"en\">\n")
        fullHtml.append("<head>\n")
        fullHtml.append("    $SAIF_BUILD_MARKER\n")
        fullHtml.append("    <meta charset=\"UTF-8\">\n")
        fullHtml.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover\">\n")
        fullHtml.append("    <title>$title</title>\n")
        fullHtml.append("    <style>\n")
        fullHtml.append(defaultVariables).append("\n\n")
        fullHtml.append(defaultReset).append("\n\n")
        if (tokensCss.isNotBlank()) fullHtml.append("/* Tokens */\n").append(tokensCss).append("\n\n")
        if (baseCss.isNotBlank()) fullHtml.append("/* Base */\n").append(baseCss).append("\n\n")
        if (componentsCss.isNotBlank()) fullHtml.append("/* Components */\n").append(componentsCss).append("\n\n")
        fullHtml.append("    </style>\n")
        fullHtml.append("</head>\n")
        fullHtml.append("<body>\n")

        if (screenHtmls.isNotBlank()) {
            fullHtml.append(screenHtmls).append("\n")
        } else {
            fullHtml.append("    <div id=\"app-root\">\n")
            fullHtml.append("        <header class=\"app-header\"><h1>$title</h1></header>\n")
            fullHtml.append("        <main id=\"main-content\"></main>\n")
            fullHtml.append("    </div>\n")
        }

        fullHtml.append("    <script>\n")
        fullHtml.append("/* Safe State Store with localStorage try/catch */\n")
        fullHtml.append(stateJs).append("\n\n")
        fullHtml.append("/* UI Router, Modal & Toast Helpers */\n")
        fullHtml.append(uiJs).append("\n\n")
        if (featureJs.isNotBlank()) {
            fullHtml.append("/* Features */\n").append(featureJs).append("\n\n")
        }
        fullHtml.append("/* Main Entrypoint */\n")
        fullHtml.append(mainJs).append("\n")
        fullHtml.append("    </script>\n")
        fullHtml.append("</body>\n")
        fullHtml.append("</html>\n")

        return fullHtml.toString()
    }

    private fun defaultStateJs() = """
        window.SaifStore = (() => {
            const memStore = {};
            return {
                get: (k, fallback = null) => {
                    try {
                        const val = localStorage.getItem(k);
                        return val ? JSON.parse(val) : (memStore[k] ?? fallback);
                    } catch (e) {
                        return memStore[k] ?? fallback;
                    }
                },
                set: (k, v) => {
                    memStore[k] = v;
                    try {
                        localStorage.setItem(k, JSON.stringify(v));
                    } catch (e) {
                        console.warn('localStorage setItem failed, used in-memory fallback', e);
                    }
                }
            };
        })();
    """.trimIndent()

    private fun defaultUiJs() = """
        window.SaifUI = (() => {
            return {
                toast: (msg, duration = 3000) => {
                    let container = document.getElementById('saif-toast-container');
                    if (!container) {
                        container = document.createElement('div');
                        container.id = 'saif-toast-container';
                        container.style.cssText = 'position:fixed;bottom:24px;left:50%;transform:translateX(-50%);z-index:9999;display:flex;flex-direction:column;gap:8px;pointer-events:none;';
                        document.body.appendChild(container);
                    }
                    const toast = document.createElement('div');
                    toast.style.cssText = 'background:rgba(19,24,38,0.92);backdrop-filter:blur(12px);border:1px solid rgba(139,92,246,0.4);color:#F1F5F9;padding:10px 18px;border-radius:20px;font-size:13.5px;font-weight:600;box-shadow:0 8px 24px rgba(0,0,0,0.4);transition:all 0.3s ease;opacity:0;transform:translateY(10px);pointer-events:auto;';
                    toast.innerText = msg;
                    container.appendChild(toast);
                    requestAnimationFrame(() => {
                        toast.style.opacity = '1';
                        toast.style.transform = 'translateY(0)';
                    });
                    setTimeout(() => {
                        toast.style.opacity = '0';
                        toast.style.transform = 'translateY(-10px)';
                        setTimeout(() => toast.remove(), 300);
                    }, duration);
                },
                haptic: (ms = 35) => {
                    try {
                        if ('vibrate' in navigator) navigator.vibrate(ms);
                    } catch (e) {}
                }
            };
        })();
    """.trimIndent()
}
