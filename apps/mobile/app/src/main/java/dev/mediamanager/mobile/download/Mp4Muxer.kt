package dev.mediamanager.mobile.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins a video-only track and an audio track into one container without re-encoding.
 *
 * YouTube only serves muxed files up to 720p, and some videos carry none at all, so
 * the downloader fetches the two tracks separately. MP4 is tried first (H.264 + AAC),
 * WebM second (VP9/AV1 + Opus), which is what the streams actually are.
 */
internal object Mp4Muxer {

    private const val TAG = "Mp4Muxer"
    private const val BUFFER_SIZE = 1 shl 20

    /**
     * Remuxes into `<base>.mp4`, `.webm` or `.mkv`. MediaMuxer is tried first because
     * it is in-process and takes about a second; the bundled FFmpeg binary is the
     * safety net for codec pairs the platform muxer refuses.
     */
    fun mux(videoFile: File, audioFile: File, base: File, filesRoot: File): File? {
        for (extension in listOf("mp4", "webm")) {
            val output = File(base.parentFile, "${base.name}.$extension")
            if (remux(videoFile, audioFile, output, extension)) return output
            output.delete()
        }
        return ffmpegRemux(videoFile, audioFile, base, filesRoot)
    }

    private fun ffmpegRemux(videoFile: File, audioFile: File, base: File, filesRoot: File): File? {
        val binary = ffmpegBinary(filesRoot) ?: return null
        for (extension in listOf("mp4", "mkv")) {
            val output = File(base.parentFile, "${base.name}.$extension")
            val ok = runCatching {
                val process = ProcessBuilder(
                    binary.absolutePath,
                    "-hide_banner", "-loglevel", "error", "-y",
                    "-i", videoFile.absolutePath,
                    "-i", audioFile.absolutePath,
                    "-map", "0:v:0", "-map", "1:a:0",
                    "-c", "copy",
                    output.absolutePath,
                ).redirectErrorStream(true).start()
                val log = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                if (code != 0) Log.w(TAG, "ffmpeg $extension failed ($code): $log")
                code == 0 && output.length() > 0
            }.getOrDefault(false)
            if (ok) return output
            output.delete()
        }
        return null
    }

    private fun ffmpegBinary(filesRoot: File): File? = runCatching {
        File(filesRoot, "youtubedl-android").walkTopDown()
            .firstOrNull { it.isFile && (it.name == "ffmpeg" || it.name == "ffmpeg.bin") }
    }.getOrNull()

    private fun remux(videoFile: File, audioFile: File, output: File, extension: String): Boolean =
        runCatching {
            val video = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
            val audio = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            try {
                val videoTrack = firstTrack(video, "video/")
                val audioTrack = firstTrack(audio, "audio/")
                if (videoTrack < 0 || audioTrack < 0) return false

                video.selectTrack(videoTrack)
                audio.selectTrack(audioTrack)
                val format = if (extension == "webm") {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                } else {
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }
                val muxer = MediaMuxer(output.absolutePath, format)
                try {
                    val outVideo = muxer.addTrack(video.getTrackFormat(videoTrack))
                    val outAudio = muxer.addTrack(audio.getTrackFormat(audioTrack))
                    muxer.start()
                    copy(video, muxer, outVideo, videoTrack)
                    copy(audio, muxer, outAudio, audioTrack)
                    muxer.stop()
                } finally {
                    muxer.release()
                }
                output.length() > 0
            } finally {
                video.release()
                audio.release()
            }
        }.onFailure { Log.w(TAG, "remux to $extension failed: ${it.message}") }
            .getOrDefault(false)

    private fun firstTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(mimePrefix) == true) return index
        }
        return -1
    }

    private fun copy(extractor: MediaExtractor, muxer: MediaMuxer, outTrack: Int, track: Int) {
        // 4K keyframes can exceed 1 MiB; allocating too little throws and costs a
        // whole ffmpeg pass, so ask the track for its maximum sample size.
        val maxSample = runCatching {
            extractor.getTrackFormat(track).getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
        }.getOrDefault(0)
        val buffer = ByteBuffer.allocateDirect(maxSample.coerceAtLeast(BUFFER_SIZE))
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else {
                0
            }
            muxer.writeSampleData(outTrack, buffer, info)
            extractor.advance()
        }
    }
}
