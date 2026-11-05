package com.example.advance

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * SAIF ADVANCE EDITING ENGINE — the engine bundle.
 *
 * The complete engine (IDE bridge in Go, the Flow browser extension, the Termux daemon, the proto toolkit
 * and every spec document) ships inside the APK as read-only assets under `assets/saif_engine/`.
 *
 * Android cannot hand its private assets to Termux or to a browser, so this object packs the whole tree
 * into ONE zip and publishes it to the public Download/SAIF_AI_Engine/ folder. From there:
 *   • Termux can unzip it and build the Go bridge / run the Python tools,
 *   • Kiwi (or desktop Chrome) can load `extensions/google-flow` as an unpacked extension.
 *
 * Every path and number shown in the UI comes from a real file in the APK — nothing is simulated.
 */
object SaifEngine {

    const val ASSET_ROOT = "saif_engine"
    const val ZIP_NAME = "SAIF_AI_Engine.zip"
    const val FOLDER = "SAIF_AI_Engine"
    const val TERMUX_DIR = "saif_engine"
    private const val PREFS = "saif_engine"

    const val TERMUX_ZIP = "~/storage/shared/Download/$FOLDER/$ZIP_NAME"
    const val TERMUX_ENGINE = "~/saif_engine"

    data class Part(
        val id: String,
        val title: String,
        val what: String,
        val folder: String,
        val commands: List<Pair<String, String>>   // label to command
    )

    /** The five real parts of the engine, with the commands that actually use them. */
    val PARTS: List<Part> = listOf(
        Part("bridge", "IDE bridge (Go)", "WebSocket hub, Claude providers, git service, plugins, attachments, rewind, voice — the whole bridge server.", "agy_ide_bridge",
            listOf("Go install karo" to "pkg install -y golang",
                "Build karo" to "cd $TERMUX_ENGINE/agy_ide_bridge && go mod tidy && go build -o saif-bridge .",
                "Bridge chalao" to "cd $TERMUX_ENGINE/agy_ide_bridge && ./saif-bridge")),
        Part("extension", "Flow extension (MV3)", "Chrome extension: Flow client, workflows, storyboard analyzer and 65 MCP tools. Same files, SAIF AI naam ke saath.", "extensions/google-flow",
            listOf("Extension zip banao" to "cd $TERMUX_ENGINE/extensions && zip -r ~/storage/shared/Download/$FOLDER/saif-flow-extension.zip google-flow",
                "Termux me copy" to "cp -r $TERMUX_ENGINE/extensions/google-flow ~/storage/shared/Download/$FOLDER/"),
            ),
        Part("daemon", "Termux daemon (Go)", "Daemon jo Termux ke andar background me chalta hai.", "termux-daemon",
            listOf("Build karo" to "cd $TERMUX_ENGINE/termux-daemon && go mod init saif-daemon 2>/dev/null; go build -o saif-daemon server.go",
                "Daemon chalao" to "cd $TERMUX_ENGINE/termux-daemon && ./saif-daemon")),
        Part("proto", "Proto toolkit (Python)", "Local gRPC/proto definitions aur unke extractors (python).", "proto_generator",
            listOf("Python install" to "pkg install -y python",
                "Types dump karo" to "cd $TERMUX_ENGINE/proto_generator && python dump_all_local_types.py",
                "Filter chalao" to "cd $TERMUX_ENGINE/proto_generator && python filter_proto.py --help")),
        Part("docs", "Specs & MCP docs", "google-flow-mcp-spec, CLAUDE CLI protocol, extension docs aur examples — sab app ke andar padh sakte ho.", "extension_docs",
            listOf("Sahi jagah dekho" to "cd $TERMUX_ENGINE && ls extension_docs extensions"))
    )

    /** Documents that can be read inside the app (real files from the APK). */
    val DOCS: List<Pair<String, String>> = listOf(
        "Overview" to "$ASSET_ROOT/SAIF_ENGINE_README.md",
        "Flow MCP spec" to "$ASSET_ROOT/google-flow-mcp-spec.md",
        "CLAUDE CLI protocol" to "$ASSET_ROOT/CLAUDE_CLI_PROTOCOL.md",
        "Extensions README" to "$ASSET_ROOT/extensions/README.md",
        "Extension lifecycle" to "$ASSET_ROOT/extension_docs/lifecycle.md",
        "MCP guide" to "$ASSET_ROOT/extension_docs/mcp.md",
        "Chrome API" to "$ASSET_ROOT/extension_docs/chrome-api.md",
        "Developer MCP" to "$ASSET_ROOT/extension_docs/developer-mcp.md",
        "Google Flow tools" to "$ASSET_ROOT/extension_docs/google-flow.md",
        "Skills" to "$ASSET_ROOT/extension_docs/skills.md",
        "Plugin repository" to "$ASSET_ROOT/extension_docs/plugin-repository.md",
        "PC development" to "$ASSET_ROOT/extension_docs/pc-development.md"
    )

    fun assetFiles(context: Context): List<String> {
        val out = ArrayList<String>()
        fun walk(path: String) {
            val children = runCatching { context.assets.list(path) }.getOrNull() ?: return
            for (name in children) {
                val child = if (path.isEmpty()) name else "$path/$name"
                val sub = runCatching { context.assets.list(child) }.getOrNull()
                if (sub != null && sub.isNotEmpty()) walk(child) else out += child
            }
        }
        walk(ASSET_ROOT)
        return out
    }

    /** file count and total bytes of the bundled engine. */
    fun summary(context: Context): Pair<Int, Long> {
        val files = assetFiles(context)
        var bytes = 0L
        for (f in files) bytes += runCatching { context.assets.open(f).use { it.available().toLong() } }.getOrDefault(0L)
        return files.size to bytes
    }

    /** Files in one part, e.g. "agy_ide_bridge" or "extensions". */
    fun countIn(context: Context, folder: String): Int =
        assetFiles(context).count { it.removePrefix("$ASSET_ROOT/").startsWith(folder) }

    fun readText(context: Context, path: String): String =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
            .getOrElse { "File padhi nahi ja saki: $path\n${it.message ?: ""}" }

    fun buildZip(context: Context, out: File): Int {
        val files = assetFiles(context)
        if (files.isEmpty()) throw IllegalStateException("APK mein engine files nahi mili (assets/$ASSET_ROOT).")
        out.parentFile?.mkdirs()
        var count = 0
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            for (path in files) {
                val rel = path.removePrefix("$ASSET_ROOT/")
                val data = context.assets.open(path).use { it.readBytes() }
                zos.putNextEntry(ZipEntry(rel))
                zos.write(data)
                zos.closeEntry()
                count++
            }
        }
        return count
    }

    fun publishedPath(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("zip_path", null)

    /** Packs the engine and publishes it to Download/SAIF_AI_Engine/. Returns the visible path. */
    fun publish(context: Context): Result<String> = runCatching {
        val zip = File(context.cacheDir, ZIP_NAME)
        val count = buildZip(context, zip)
        if (count <= 0) throw IllegalStateException("Engine pack nahi hua.")
        val shown: String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, ZIP_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Downloads folder mein file create nahi hui.")
            resolver.openOutputStream(uri)?.use { os -> zip.inputStream().use { it.copyTo(os) } }
                ?: throw IllegalStateException("Downloads file kholi nahi ja saki.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "Download/$FOLDER/$ZIP_NAME"
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            val dest = File(dir, ZIP_NAME)
            zip.copyTo(dest, overwrite = true)
            dest.absolutePath
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("zip_path", shown).putInt("files", count).putLong("at", System.currentTimeMillis()).apply()
        runCatching { zip.delete() }
        shown
    }
}
