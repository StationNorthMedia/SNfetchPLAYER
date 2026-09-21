package net.sn.fetchplayer.extractor

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder().url(url)

        var hasUserAgent = false
        var hasAcceptLanguage = false
        var hasCookie = false

        headers.forEach { (headerName, headerValues) ->
            if (headerName.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
            if (headerName.equals("Accept-Language", ignoreCase = true)) hasAcceptLanguage = true
            if (headerName.equals("Cookie", ignoreCase = true)) hasCookie = true

            headerValues.forEach { value ->
                builder.addHeader(headerName, value)
            }
        }

        if (!hasUserAgent) {
            builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
        }

        if (!hasAcceptLanguage) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        if (!hasCookie && url.contains("youtube.com")) {
            builder.header("Cookie", "SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmRlIAEaBgiA_LyaBg; CONSENT=YES+1; PREF=f6=40000000&hl=en")
        }

        when (httpMethod.uppercase()) {
            "GET" -> builder.get()
            "POST" -> {
                val body = dataToSend?.toRequestBody() ?: "".toRequestBody()
                builder.post(body)
            }
            "HEAD" -> builder.head()
            else -> builder.get()
        }

        val okResponse = client.newCall(builder.build()).execute()
        val responseBody = okResponse.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()

        okResponse.headers.names().forEach { name ->
            responseHeaders[name] = okResponse.headers.values(name)
        }

        val latestUrl = okResponse.request.url.toString()

        return Response(
            okResponse.code,
            okResponse.message,
            responseHeaders,
            responseBody,
            latestUrl
        )
    }
}
