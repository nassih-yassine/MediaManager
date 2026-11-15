package dev.mediamanager.mobile.download

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.media.LibraryPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadSettings(val wifiOnly: Boolean = false, val maxHeight: Int = 1080)

/** Options that take a value; everything else in a flat arg list is a flag. */
private val valueFlags = setOf(
    "-o", "--socket-timeout", "--retries", "--audio-format", "--audio-quality",
    "-f", "--merge-output-format", "--js-runtimes", "--convert-thumbnails",
)

fun YoutubeDLRequest.addFlatArgs(args: List<String>) {
    var i = 0
    while (i < args.size) {
        val token = args[i]
        if (token in valueFlags && i + 1 < args.size) {
            addOption(token, args[i + 1])
            i += 2
        } else {
            addOption(token)
            i++
        }
    }
}

class YtDlpExtractor(
    private val context: Context,
    private val paths: LibraryPaths,
    private val settings: () -> DownloadSettings,
) : Extractor {

    companion object {
        @Volatile
        var initialized = false
            private set

        fun init(context: Context) {
            if (initialized) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            initialized = true
        }

        fun version(): String? = runCatching { YoutubeDL.getInstance().version(null) }.getOrNull()
    }

    override suspend fun fetch(job: JobEntity, onProgress: (Float) -> Unit): FetchResult =
        withContext(Dispatchers.IO) {
            init(context)
            val dir = paths.incomingDir(job.id).apply { mkdirs() }
            val mode = DownloadMode.from(job.mode)

            fun argsFor(jsRuntime: String?, extractorArgs: String?): List<String> = when (mode) {
                DownloadMode.AUDIO -> YtDlpArgs.audio(job.url, dir, jsRuntime, extractorArgs)
                DownloadMode.VIDEO -> YtDlpArgs.video(job.url, dir, settings().maxHeight, jsRuntime, extractorArgs)
            }

            fun execute(args: List<String>) {
                val request = YoutubeDLRequest(job.url).apply { addFlatArgs(args.dropLast(1)) }
                YoutubeDL.getInstance().execute(request, job.id) { progress, _, line ->
                    val parsed = parseProgress(line)
                    when {
                        parsed != null -> onProgress(parsed)
                        progress >= 0f -> onProgress((progress / 100f).coerceIn(0f, 1f))
                    }
                }
            }

            /*
             * YouTube serves 403 on its media URLs when the signature cannot be solved.
             * The phone has no Node, so the fallback is a client that still hands out
             * unsigned formats: android_vr first, then tv. Non-YouTube URLs get one try.
             */
            val plans: List<Pair<String?, String?>> = if (job.url.contains("youtu", ignoreCase = true)) {
                listOf(
                    "quickjs" to null,
                    "quickjs" to "youtube:player_client=android_vr",
                    "quickjs" to "youtube:player_client=tv",
                    null to "youtube:player_client=android_vr",
                )
            } else {
                listOf("quickjs" to null, null to null)
            }

            var lastError: Exception? = null
            for ((jsRuntime, extractorArgs) in plans) {
                try {
                    execute(argsFor(jsRuntime, extractorArgs))
                    lastError = null
                    break
                } catch (e: Exception) {
                    lastError = e
                    val recoverable = e.message?.let {
                        it.contains("403", ignoreCase = true) ||
                            it.contains("signature", ignoreCase = true) ||
                            it.contains("js-runtimes", ignoreCase = true) ||
                            it.contains("player response", ignoreCase = true)
                    } ?: false
                    if (!recoverable) throw e
                    Log.w("YtDlp", "attempt failed (${jsRuntime ?: "no-js"}, $extractorArgs): ${e.message}")
                }
            }
            lastError?.let { throw it }

            val infoJson = dir.listFiles()?.firstOrNull { it.name.endsWith(".info.json") }
            val info = infoJson?.readText()?.let(::parseInfo)
                ?: MediaInfo(
                    title = job.url.substringAfterLast('/').ifBlank { "Download" },
                    durationMs = null,
                    ext = if (mode == DownloadMode.AUDIO) "mp3" else "mp4",
                    width = null,
                    height = null,
                    approxBytes = null,
                )
            val media = findMediaFile(dir, info.ext)
                ?: throw IllegalStateException("ERROR: the download produced no media file")
            Log.i("YtDlp", "downloaded ${media.name} (${media.length()} bytes) for job ${job.id}")
            FetchResult(info = info, mediaFile = media, thumbFile = findThumbFile(dir))
        }

    fun cancel(jobId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(jobId) }
    }
}
