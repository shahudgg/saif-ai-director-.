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
 * SAIF AI ADVANCE EDITING — engine installer.
 *
 * The complete production engine (core/*.py, skills/, fonts/, elements/, styles/, docs) ships inside the
 * APK as read-only assets under `assets/saif_advance/`. Android apps cannot hand their private assets to
 * another app, so this object does the one thing that actually works:
 *
 *   1. packs the whole asset tree into ONE zip (`SAIF_AI_Advance_engine.zip`),
 *   2. publishes that zip into the phone's public **Download/SAIF_AI_Advance/** folder (MediaStore on
 *      Android 10+, direct file on older versions),
 *   3. hands Termux a single command that unzips it into `~/saif_advance` — the exact same file tree,
 *      same names, same code.
 *
 * Every path is real and reported back to the owner; nothing is faked.
 */
object AdvanceEngine {

    const val ASSET_ROOT = "saif_advance"
    const val ZIP_NAME = "SAIF_AI_Advance_engine.zip"
    const val FOLDER = "SAIF_AI_Advance"
    const val TERMUX_DIR = "saif_advance"
    private const val PREFS = "saif_advance_engine"

    /** Termux path of the published zip (after `termux-setup-storage`). */
    const val TERMUX_ZIP = "~/storage/shared/Download/$FOLDER/$ZIP_NAME"

    /** Commands reference this folder after unzipping. */
    const val TERMUX_ENGINE = "~/saif_advance"

    data class Step(val n: Int, val title: String, val what: String, val command: String)

    /** The engine's own 9-step production protocol, in order. */
    val STEPS: List<Step> = listOf(
        Step(0, "Engine install karo", "Zip ko Termux ke ~/saif_advance mein unzip karta hai (poora engine).",
            "cd ~ && rm -rf $TERMUX_DIR && mkdir -p $TERMUX_DIR && unzip -o $TERMUX_ZIP -d $TERMUX_DIR >/dev/null && " +
                "echo 'engine files:' && find $TERMUX_DIR -type f | wc -l"),
        Step(1, "Dependencies", "Python, ffmpeg aur engine ki libraries (faster-whisper, pillow, numpy, scipy, soundfile).",
            "pkg update -y && pkg install -y python ffmpeg unzip && pip install --upgrade pip && " +
                "pip install faster-whisper pillow numpy scipy soundfile"),
        Step(2, "Self-check", "Engine ke teen core modules khud ko verify karte hain.",
            "cd $TERMUX_ENGINE && python core/render.py selfcheck; python core/trends.py --selfcheck; python core/cutout.py --demo"),
        Step(3, "Trend scan", "YouTube / X / Reddit se real-time trend scan (topic chunne ke liye).",
            "cd $TERMUX_ENGINE && python core/trends.py --sources youtube,twitter,reddit"),
        Step(4, "Voiceover align", "VO.wav ko LINES.tsv ke saath word-level align karta hai (timing plan banata hai).",
            "cd $TERMUX_ENGINE && python core/align.py --vo VO.wav --lines LINES.tsv"),
        Step(5, "Element cutouts", "elements/ ke magenta cutouts ko key + clean karta hai.",
            "cd $TERMUX_ENGINE && python core/elements.py"),
        Step(6, "Plan + shots", "Timing plan check karta hai aur shots parallel render karta hai.",
            "cd $TERMUX_ENGINE && python core/render.py plan && python core/render.py shots"),
        Step(7, "Audio mix", "Broadcast mix with voice ducking (-14 LUFS).",
            "cd $TERMUX_ENGINE && python core/mix.py cues.tsv --vo VO.wav --words WORDS.tsv --lines VO.tsv --out mix.wav"),
        Step(8, "Master render", "Final master video assemble karta hai.",
            "cd $TERMUX_ENGINE && python core/render.py master")
    )

    /** Every file shipped in the APK for this feature (relative to assets/saif_advance). */
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

    fun summary(context: Context): Pair<Int, Long> {
        val files = assetFiles(context)
        var bytes = 0L
        for (f in files) bytes += runCatching { context.assets.open(f).use { it.available().toLong() } }.getOrDefault(0L)
        return files.size to bytes
    }

    /** Packs the asset tree into [out]. Returns the number of files written. */
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

    fun installedPath(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("zip_path", null)

    /** Packs and publishes the engine zip into the public Downloads folder. Returns the published file path. */
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
        shown
    }

    /** Nothing is left behind in the cache after publishing. */
    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, ZIP_NAME).delete() }
    }
}
