package dev.mediamanager.mobile.download

import dev.mediamanager.mobile.data.JobEntity

/** YouTube goes through NewPipe; everything else stays on yt-dlp. */
class CompositeExtractor(
    private val newPipe: YoutubeNewPipeExtractor,
    private val ytDlp: YtDlpExtractor,
) : Extractor {

    override suspend fun fetch(job: JobEntity, onProgress: (Float) -> Unit): FetchResult =
        if (isYouTubeUrl(job.url)) newPipe.fetch(job, onProgress) else ytDlp.fetch(job, onProgress)

    fun cancel(jobId: String) = ytDlp.cancel(jobId)
}
