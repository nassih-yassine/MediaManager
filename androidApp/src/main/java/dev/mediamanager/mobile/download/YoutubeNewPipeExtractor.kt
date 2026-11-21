package dev.mediamanager.mobile.download

import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.media.LibraryPaths
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * YouTube without yt-dlp. NewPipe's extractor asks YouTube the way a mobile app
 * does, so it gets playable URLs without a JS runtime or PO token. Audio comes
 * back as m4a/opus; nothing is transcoded.
 */
class YoutubeNewPipeExtractor(private val paths: LibraryPaths, private val maxVideoHeight: () -> Int = { 1080 }) : Extractor {

    companion object {
        // A 3-minute track is ~3 MB. With an 8 MB floor every song took the
        // single-connection path, which googlevideo throttles to a few hundred KB/s.
        private const val PARALLEL_MIN_BYTES = 1_200_000L
        private const val MIN_PARTS = 2
        private const val MAX_PARTS = 8
        private const val BYTES_PER_PART = 1_500_000L

        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    }

    override suspend fun fetch(
        job: JobEntity,
        onProgress: (Float) -> Unit,
        onStage: (String) -> Unit,
        onMeta: (String, Long?, File?) -> Unit,
    ): FetchResult = withContext(Dispatchers.IO) {
        NewPipeInit.ensure()
        onStage("extracting")
        val url = normalizeUrl(job.url)
        val info = try {
            StreamInfo.getInfo(ServiceList.YouTube, url)
        } catch (e: Exception) {
            throw IllegalStateException(e.message ?: "ERROR: YouTube extraction failed", e)
        }
        val dir = paths.incomingDir(job.id).apply { mkdirs() }
        val baseName = info.id ?: job.id

        // Thumbnail first: the row shows title, duration and art while it downloads.
        val previewThumb = info.thumbnails.maxByOrNull { it.height }?.let { image ->
            val file = File(dir, "$baseName.jpg")
            runCatching {
                download(image.url, file) {}
                file
            }.getOrNull()
        }
        onMeta(info.name ?: "YouTube video", info.duration.takeIf { it > 0 }?.times(1000), previewThumb)

        android.util.Log.d(
            "NewPipe",
            "streams: video=${info.videoStreams.size} videoOnly=${info.videoOnlyStreams.size} " +
                "audio=${info.audioStreams.size} " +
                "heights=${info.videoStreams.map { it.height }}${info.videoOnlyStreams.map { it.height }} " +
                "delivery=${info.videoOnlyStreams.map { it.deliveryMethod }}",
        )
        val media: File
        val ext: String
        var width: Int? = null
        var height: Int? = null
        if (DownloadMode.from(job.mode) == DownloadMode.AUDIO) {
            val stream = bestAudio(info.audioStreams)
                ?: throw IllegalStateException("ERROR: no audio stream available for this video")
            onStage("downloading")
            ext = stream.format?.suffix ?: "m4a"
            media = File(dir, "$baseName.$ext")
            download(stream.url ?: throw IllegalStateException("ERROR: no playable audio URL"), media, onProgress)
        } else {
            onStage("downloading")
            // Muxed streams need no remux but YouTube caps them around 720p. Take a
            // separate pair whenever it is taller, otherwise stay with the easy file.
            val muxed = bestVideo(info.videoStreams, maxVideoHeight())
            val videoOnly = bestVideoOnly(info.videoOnlyStreams, maxVideoHeight())
            val preferSeparate = videoOnly != null && (muxed == null || videoOnly.height > muxed.height)
            if (muxed != null && !preferSeparate) {
                ext = muxed.format?.suffix ?: "mp4"
                media = File(dir, "$baseName.$ext")
                width = muxed.width.takeIf { it > 0 }
                height = muxed.height.takeIf { it > 0 }
                download(
                    muxed.url ?: throw IllegalStateException("ERROR: no playable video URL"),
                    media,
                    onProgress,
                )
            } else {
                // No usable muxed stream: fetch picture and sound separately, then remux.
                val videoOnly = videoOnly
                    ?: throw IllegalStateException("ERROR: no video stream available for this video")
                val audio = bestAudioFor(
                    info.audioStreams,
                    videoOnly.format?.suffix.equals("mp4", ignoreCase = true),
                ) ?: throw IllegalStateException("ERROR: this video has no audio track")
                val videoFile = File(dir, "$baseName.v.${videoOnly.format?.suffix ?: "mp4"}")
                val audioFile = File(dir, "$baseName.a.${audio.format?.suffix ?: "m4a"}")
                coroutineScope {
                    val videoJob = async(Dispatchers.IO) {
                        download(
                            videoOnly.url ?: throw IllegalStateException("ERROR: no playable video URL"),
                            videoFile,
                        ) { progress -> onProgress(progress * 0.85f) }
                    }
                    val audioJob = async(Dispatchers.IO) {
                        download(
                            audio.url ?: throw IllegalStateException("ERROR: no playable audio URL"),
                            audioFile,
                        ) { progress -> onProgress(0.85f + progress * 0.15f) }
                    }
                    videoJob.await()
                    audioJob.await()
                }
                onStage("muxing")
                val combined = Mp4Muxer.mux(videoFile, audioFile, File(dir, baseName), paths.filesRoot)
                videoFile.delete()
                audioFile.delete()
                if (combined == null) {
                    throw IllegalStateException("ERROR: could not combine the video and audio tracks")
                }
                media = combined
                ext = combined.extension
                width = videoOnly.width.takeIf { it > 0 }
                height = videoOnly.height.takeIf { it > 0 }
                onProgress(1f)
            }
        }

        FetchResult(
            info = MediaInfo(
                title = info.name ?: "YouTube video",
                durationMs = info.duration.takeIf { it > 0 }?.times(1000),
                ext = ext,
                width = width,
                height = height,
                approxBytes = media.length().takeIf { it > 0 },
            ),
            mediaFile = media,
            thumbFile = previewThumb,
        )
    }

    private suspend fun download(url: String, target: File, onProgress: (Float) -> Unit): Unit = coroutineScope {
        val length = contentLength(url)
        val parallel = length != null && length > PARALLEL_MIN_BYTES && supportsRanges(url)
        if (parallel && length != null) {
            parallelDownload(url, target, length, onProgress)
        } else {
            downloadTo(url, target, range = null) { delta, total ->
                if (total > 0) onProgress((delta.toFloat() / total).coerceIn(0f, 1f))
            }
        }
    }

    /**
     * Four ranged connections in parallel. Single-connection downloads from
     * googlevideo are throttled hard; splitting the file is the difference
     * between a few hundred KB/s and tens of MB/s.
     */
    private suspend fun parallelDownload(url: String, target: File, length: Long, onProgress: (Float) -> Unit): Unit = coroutineScope {
        // More connections for bigger files: 2 for a short track, up to 8 for a film.
        val parts = (length / BYTES_PER_PART).toInt().coerceIn(MIN_PARTS, MAX_PARTS)
        val chunk = length / parts
        val meter = ProgressMeter(length)
        // Each part reports cumulative bytes; remembering the previous value keeps the
        // shared fraction honest instead of summing the same bytes over and over.
        val lastRead = java.util.concurrent.atomic.AtomicLongArray(parts)
        val partFiles = (0 until parts).map { index ->
            File(target.parentFile, "${target.name}.part$index")
        }

        partFiles.mapIndexed { index, part ->
            async(Dispatchers.IO) {
                val start = index * chunk
                val end = if (index == parts - 1) length - 1 else start + chunk - 1
                downloadTo(url, part, range = start to end) { read, _ ->
                    val previous = lastRead.getAndSet(index, read)
                    onProgress(meter.add(read - previous))
                }
            }
        }.awaitAll()

        java.io.BufferedOutputStream(target.outputStream(), 1 shl 20).use { out ->
            partFiles.forEach { part ->
                part.inputStream().use { it.copyTo(out) }
                part.delete()
            }
        }
        onProgress(1f)
    }

    private fun contentLength(url: String): Long? = runCatching {
        val connection = open(url)
        connection.requestMethod = "HEAD"
        connection.connect()
        val length = connection.contentLengthLong
        connection.disconnect()
        length.takeIf { it > 0 }
    }.getOrNull()

    private fun supportsRanges(url: String): Boolean = runCatching {
        val connection = open(url)
        connection.setRequestProperty("Range", "bytes=0-0")
        val ok = connection.responseCode == 206
        connection.disconnect()
        ok
    }.getOrDefault(false)

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", USER_AGENT)
    }

    private suspend fun downloadTo(
        url: String,
        target: File,
        range: Pair<Long, Long>?,
        onProgress: (bytesRead: Long, contentLength: Long) -> Unit,
    ) {
        val connection = open(url)
        try {
            if (range != null) {
                connection.setRequestProperty("Range", "bytes=${range.first}-${range.second}")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("ERROR: HTTP $code from the media server")
            }
            val total = if (range != null) (range.second - range.first + 1) else connection.contentLengthLong
            var read = 0L
            java.io.BufferedOutputStream(target.outputStream(), 1 shl 20).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            if (read == 0L) throw IllegalStateException("ERROR: the media server returned no data")
        } finally {
            connection.disconnect()
        }
    }

    private fun bestAudio(streams: List<AudioStream>): AudioStream? = streams.filter { it.isUrl }
        .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }

    /**
     * Video-only streams come as MP4/H.264 or WebM/VP9. H.264 remuxes into MP4 with
     * AAC audio, which every player on the phone handles, so it wins over a taller WebM.
     */
    private fun bestAudioFor(streams: List<AudioStream>, mp4: Boolean): AudioStream? {
        val usable = streams.filter { it.isUrl }
        val preferred = usable.filter {
            it.format?.suffix.equals(if (mp4) "m4a" else "webm", ignoreCase = true)
        }
        return (preferred.ifEmpty { usable })
            .maxByOrNull { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
    }

    private fun bestVideoOnly(streams: List<VideoStream>, maxHeight: Int): VideoStream? {
        val usable = streams.filter { it.isUrl }
        val withinCap = usable.filter { it.height <= maxHeight }.ifEmpty { usable }
        return withinCap
            .filter { it.format?.suffix.equals("mp4", ignoreCase = true) }
            .maxByOrNull { it.height }
            ?: withinCap.maxByOrNull { it.height }
    }

    private fun bestVideo(streams: List<VideoStream>, maxHeight: Int): VideoStream? {
        val usable = streams.filter { it.isUrl }
        val withinCap = usable.filter { it.height <= maxHeight }
        return (withinCap.ifEmpty { usable }).maxByOrNull { it.height }
    }
}
