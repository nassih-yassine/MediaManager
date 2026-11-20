package dev.mediamanager.mobile.download

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal bridge between NewPipe's extractor and the platform HTTP stack.
 * Reads the whole body into a string, which is what Response expects.
 */
object NewPipeDownloader : Downloader() {

    override fun execute(request: Request): Response {
        val connection = (URL(request.url()).openConnection() as HttpURLConnection).apply {
            requestMethod = request.httpMethod()
            connectTimeout = 20_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            request.headers().forEach { (name, values) ->
                values.forEach { value -> addRequestProperty(name, value) }
            }
            val body = request.dataToSend()
            if (body != null && body.isNotEmpty()) {
                doOutput = true
                outputStream.use { it.write(body) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Response(
                code,
                connection.responseMessage,
                connection.headerFields.filterKeys { it != null },
                text,
                connection.url.toString(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
