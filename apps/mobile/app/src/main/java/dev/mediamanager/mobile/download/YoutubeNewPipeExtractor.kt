package dev.mediamanager.mobile.download

import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.media.LibraryPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * YouTube without yt-dlp. NewPipe's extractor asks YouTube the way a mobile app
 * does, so it gets playable URLs without a JS runtime or PO token. Audio comes
 * back as m4a/opus; nothing is transcoded.
 */
class YoutubeNewPipeExtractor(
    private val paths: LibraryPaths,
    private val maxVideoHeight: () -> Int = { 1080 },
) : Extractor {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

        @Volatile
        private var initialized = false

        private fun ensureInit() {
            if (initialized) return
            NewPipe.init(NewPipeDownloader)
            initialized = true
        }
    }

    override suspend fun fetch(
        job: JobEntity,
        onProgress: (Float) -> Unit,
        onStage: (String) -> Unit,
    ): FetchResult =
        withContext(Dispatchers.IO) {
            ensureInit()
            onStage("extracting")
            val url = normalizeUrl(job.url)
            val info = try {
                StreamInfo.getInfo(ServiceList.YouTube, url)
            } catch (e: Exception) {
                throw IllegalStateException(e.message ?: "ERROR: YouTube extraction failed", e)
            }
            val dir = paths.incomingDir(job.id).apply { mkdirs() }
            val baseName = info.id ?: job.id

            val media: File
            val ext: String
            if (DownloadMode.from(job.mode) == DownloadMode.AUDIO) {
                val stream = bestAudio(info.audioStreams)
                    ?: throw IllegalStateException("ERROR: no audio stream available for this video")
                onStage("downloading")
                ext = stream.format?.suffix ?: "m4a"
                media = File(dir, "$baseName.$ext")
                download(stream.url ?: throw IllegalStateException("ERROR: no playable audio URL"), media, onProgress)
            } else {
                val stream = bestVideo(info.videoStreams, maxVideoHeight())
                    ?: throw IllegalStateException("ERROR: no video stream available for this video")
                onStage("downloading")
                ext = stream.format?.suffix ?: "mp4"
                media = File(dir, "$baseName.$ext")
                download(stream.url ?: throw IllegalStateException("ERROR: no playable video URL"), media, onProgress)
            }

            val thumb = info.thumbnails.maxByOrNull { it.height }?.let { image ->
                val file = File(dir, "$baseName.jpg")
                runCatching { download(image.url, file) {}; file }.getOrNull()
            }

            FetchResult(
                info = MediaInfo(
                    title = info.name ?: "YouTube video",
                    durationMs = info.duration.takeIf { it > 0 }?.times(1000),
                    ext = ext,
                    width = null,
                    height = null,
                    approxBytes = media.length().takeIf { it > 0 },
                ),
                mediaFile = media,
                thumbFile = thumb,
            )
        }

    private fun download(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("ERROR: HTTP ${connection.responseCode} from the media server")
            }
            val total = connection.contentLengthLong
            var read = 0L
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (read == 0L) throw IllegalStateException("ERROR: the media server returned no data")
        } finally {
            connection.disconnect()
        }
    }

    private fun bestAudio(streams: List<AudioStream>): AudioStream? =
        streams.filter { it.isUrl }
            .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }

    private fun bestVideo(streams: List<VideoStream>, maxHeight: Int): VideoStream? {
        val usable = streams.filter { it.isUrl }
        val withinCap = usable.filter { it.height <= maxHeight }
        return (withinCap.ifEmpty { usable }).maxByOrNull { it.height }
    }

}
