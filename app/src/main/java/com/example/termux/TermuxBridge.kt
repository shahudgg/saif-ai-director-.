package com.example.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Round 14 — TERMUX BRIDGE.
 *
 * The owner asked for Termux so real shell commands (ffmpeg, file moves, batch renames) can run while
 * a video is being built. Two honest paths, no lies:
 *
 *  1. **Termux** (if installed): the official `com.termux.RUN_COMMAND` intent. It needs Termux's
 *     "allow-external-apps=true" setting; if that is off we say exactly that and offer the copy button.
 *  2. **App shell**: `/system/bin/sh` inside SAIF AI's own sandbox. Works with no extra app, but the
 *     sandbox only sees app-private files, so it is used for file/format work on our own media.
 *
 * Nothing here requests root and no command is ever run without the owner pressing Run.
 */
object TermuxBridge {
    const val TERMUX_PACKAGE = "com.termux"

    data class Preset(val label: String, val command: String, val note: String)

    val PRESETS: List<Preset> = listOf(
        Preset("FFmpeg installed?", "command -v ffmpeg && ffmpeg -version | head -3", "Checks whether Termux has ffmpeg ready."),
        Preset("List Flow downloads", "ls -lh ./flow_downloads 2>/dev/null | head -40", "App sandbox only."),
        Preset("Convert PNG -> JPG (batch)", "for f in ./flow_downloads/*.png; do ffmpeg -y -i \"\$f\" \"\${f%.png}.jpg\"; done",
            "Run in Termux for a full ffmpeg; the app sandbox has no ffmpeg."),
        Preset("Make a 9:16 cover", "ffmpeg -y -i cover.png -vf \"scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920\" cover_916.jpg",
            "Run in Termux."),
        Preset("Video from images (8 s)", "ffmpeg -y -framerate 1/8 -pattern_type glob -i './flow_downloads/*.jpg' -c:v libx264 -pix_fmt yuv420p -r 30 out.mp4",
            "Run in Termux. The app's own exporter is usually better for captions/voice.")
    )

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    }.getOrDefault(false)

    fun openTermux(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
        if (intent == null) return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    /**
     * Runs one command in Termux through the official RUN_COMMAND intent. The command is passed as an
     * argument list (never shell-interpolated by us) so quoting is predictable.
     */
    fun runInTermux(context: Context, command: String, sessionAction: String = "0"): Result<Unit> = runCatching {
        if (!isInstalled(context)) throw IllegalStateException("Termux install nahi hai (F-Droid se install karo).")
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", sessionAction)
        }
        context.startService(intent)
    }.recoverCatching { e ->
        throw IllegalStateException(
            "Termux ne command accept nahi kiya (${e.message}). Termux mein ~/.termux/termux.properties kholo aur " +
                "allow-external-apps=true set karke Termux restart karo. Tab tak neeche se command copy karke paste kar sakte ho."
        )
    }

    /** Runs a command inside SAIF AI's own sandbox. Returns combined stdout + stderr (trimmed). */
    fun runLocal(context: Context, command: String, timeoutMs: Long = 20_000L): Result<String> = runCatching {
        val workDir = File(context.filesDir, "shell").apply { mkdirs() }
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        val out = StringBuilder()
        val reader = process.inputStream.bufferedReader()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        var line = reader.readLine()
        while (line != null) {
            out.append(line).append('\n')
            if (out.length > 8000) break
            line = reader.readLine()
        }
        if (!finished) { runCatching { process.destroyForcibly() }; throw IllegalStateException("Command ${timeoutMs / 1000}s mein poori nahi hui.") }
        val text = out.toString().trim()
        if (text.isBlank()) "(no output · exit ${process.exitValue()})" else text
    }

    fun copy(context: Context, text: String) {
        runCatching {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("SAIF command", text))
        }
    }

    fun shareCommand(context: Context, command: String) {
        runCatching {
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "#!/data/data/com.termux/files/usr/bin/bash\n$command\n")
            }
            context.startActivity(Intent.createChooser(i, "Send command to Termux / notes"))
        }
    }

    fun openFdroid(context: Context) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))) }
    }

    /** Where Termux can read SAIF AI's own media (shared storage path inside Termux). */
    fun termuxPathFor(context: Context, file: File): String =
        "~/storage/shared/Android/data/" + context.packageName + "/files/" + file.relativeTo(context.filesDir).path
}
