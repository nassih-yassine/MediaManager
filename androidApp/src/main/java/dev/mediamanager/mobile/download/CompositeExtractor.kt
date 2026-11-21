package dev.mediamanager.mobile.download

import dev.mediamanager.mobile.data.JobEntity

/** YouTube goes through NewPipe; everything else stays on yt-dlp. */
class CompositeExtractor(private val newPipe: YoutubeNewPipeExtractor, private val ytDlp: YtDlpExtractor) : Extractor {

    override suspend fun fetch(
        job: JobEntity,
        onProgress: (Float) -> Unit,
        onStage: (String) -> Unit,
        onMeta: (String, Long?, java.io.File?) -> Unit,
    ): FetchResult = if (isYouTubeUrl(job.url)) {
        newPipe.fetch(job, onProgress, onStage, onMeta)
    } else {
        ytDlp.fetch(job, onProgress, onStage, onMeta)
    }

    fun cancel(jobId: String) = ytDlp.cancel(jobId)
}
