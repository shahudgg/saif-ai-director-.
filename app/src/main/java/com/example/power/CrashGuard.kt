package com.example.power

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Round 15 — CRASH GUARD.
 *
 * Why: the owner keeps saying "ye feature kaam nahi karta" and a silent crash is the most common reason.
 * This installs an uncaught-exception handler that writes the FULL stack trace + device/build info to
 * `filesDir/logs/crash_<time>.txt` BEFORE the app dies. Power Tools shows the last crash in one tap, and
 * the whole report can be shared — so the next fix is based on a real trace, not a guess.
 *
 * The previous handler is always called afterwards, so Android's own crash dialog still appears.
 */
object CrashGuard {

    private const val MAX_LOGS = 10
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            // Never swallow the crash: let the platform (or the previous handler) do its job.
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    private fun logsDir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(logsDir(context), "crash_$stamp.txt")
        val body = buildString {
            appendLine("SAIF AI crash report")
            appendLine("time: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("thread: " + thread.name)
            appendLine("device: " + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")")
            appendLine("app: " + runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull())
            appendLine()
            appendLine(error.javaClass.name + ": " + (error.message ?: ""))
            appendLine(error.stackTraceToString())
        }
        file.writeText(body, Charsets.UTF_8)
        // Keep the folder small.
        logsDir(context).listFiles { f -> f.isFile && f.name.startsWith("crash_") }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_LOGS)
            ?.forEach { runCatching { it.delete() } }
    }

    fun logs(context: Context): List<File> =
        logsDir(context).listFiles { f -> f.isFile && f.name.startsWith("crash_") }
            ?.sortedByDescending { it.name }
            ?: emptyList()

    fun lastCrashText(context: Context): String =
        logs(context).firstOrNull()?.let { runCatching { it.readText() }.getOrNull() }.orEmpty()

    fun clear(context: Context): Int {
        val all = logs(context)
        var n = 0
        all.forEach { if (runCatching { it.delete() }.getOrDefault(false)) n++ }
        return n
    }
}
