package dev.mediamanager.mobile.download

import android.util.Log
import java.io.File
import org.json.JSONObject

data class MediaInfo(val title: String, val durationMs: Long?, val ext: String, val width: Int?, val height: Int?, val approxBytes: Long?)

object YtDlpArgs {

    fun audio(url: String, outDir: File, jsRuntime: String? = "quickjs", extractorArgs: String? = null): List<String> = (
        base(outDir, jsRuntime, extractorArgs) + listOf(
            "-x",
            "--audio-format",
            "mp3",
            "--audio-quality",
            "0",
        )
        ) + url

    fun video(url: String, outDir: File, maxHeight: Int, jsRuntime: String? = "quickjs", extractorArgs: String? = null): List<String> = (
        base(outDir, jsRuntime, extractorArgs) + listOf(
            "-f",
            "bv*[height<=$maxHeight]+ba/b[height<=$maxHeight]",
            "--merge-output-format",
            "mp4",
        )
        ) + url

    private fun base(outDir: File, jsRuntime: String?, extractorArgs: String?): List<String> = buildList {
        addAll(
            listOf(
                "-o", File(outDir, "%(id)s.%(ext)s").path,
                "--no-playlist",
                "--playlist-items", "1",
                "--newline",
                "--no-warnings",
                "--socket-timeout", "20",
                "--retries", "2",
                "--concurrent-fragments", "4",
                "--http-chunk-size", "10M",
                "--write-info-json",
                "--write-thumbnail",
                "--convert-thumbnails", "jpg",
            ),
        )
        if (jsRuntime != null) addAll(listOf("--js-runtimes", jsRuntime))
        if (extractorArgs != null) addAll(listOf("--extractor-args", extractorArgs))
    }
}

fun parseInfo(json: String): MediaInfo? = try {
    val o = JSONObject(json)
    MediaInfo(
        title = o.optString("title").ifBlank { "Untitled" },
        durationMs = o.optDouble("duration").takeIf { !it.isNaN() && it > 0 }?.times(1000)?.toLong(),
        ext = o.optString("ext").ifBlank { "mp4" },
        width = o.optInt("width").takeIf { it > 0 },
        height = o.optInt("height").takeIf { it > 0 },
        approxBytes = when {
            o.has("filesize_approx") && o.optLong("filesize_approx") > 0 -> o.optLong("filesize_approx")
            o.optLong("filesize") > 0 -> o.optLong("filesize")
            else -> null
        },
    )
} catch (e: Exception) {
    Log.w("MediaInfo", "unparseable info json", e)
    null
}

fun findMediaFile(dir: File, preferredExt: String): File? {
    val media = dir.listFiles()?.filter { it.isFile }?.filterNot {
        it.name.endsWith(".json") || it.name.endsWith(".jpg") || it.name.endsWith(".jpeg") ||
            it.name.endsWith(".webp") || it.name.endsWith(".png") || it.name.endsWith(".part")
    } ?: return null
    return media.firstOrNull { it.extension.equals(preferredExt, true) } ?: media.firstOrNull()
}

fun findThumbFile(dir: File): File? = dir.listFiles()
    ?.filter { it.isFile }
    ?.firstOrNull { it.extension.lowercase() in setOf("jpg", "jpeg") }
