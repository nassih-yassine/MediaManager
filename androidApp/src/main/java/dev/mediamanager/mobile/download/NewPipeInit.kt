package dev.mediamanager.mobile.download

import org.schabi.newpipe.extractor.NewPipe

/**
 * NewPipe's extractor is a process-wide singleton. Both the stream extractor and the
 * playlist inspector need it, and initialising twice resets its internal caches, so
 * every caller goes through here.
 */
object NewPipeInit {

    @Volatile
    private var initialized = false

    fun ensure() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(NewPipeDownloader)
            initialized = true
        }
    }
}
