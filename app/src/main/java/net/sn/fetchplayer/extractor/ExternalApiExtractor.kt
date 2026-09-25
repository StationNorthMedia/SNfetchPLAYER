package net.sn.fetchplayer.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.sn.fetchplayer.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ExternalApiExtractor {

    private const val TAG = "ExternalApiExtractor"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    suspend fun resolveViaPiped(youtubeId: String, baseUrl: String, isAudioOnly: Boolean): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(4000L) {
                try {
                    val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
                    val targetUrl = "$cleanBaseUrl/streams/$youtubeId"
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "SNfetchPLAYER-App")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        AppLogger.w(TAG, "Piped instance [$cleanBaseUrl] returned HTTP ${response.code} for $youtubeId")
                        return@withTimeoutOrNull null
                    }

                    val bodyStr = response.body?.string() ?: return@withTimeoutOrNull null
                    val json = JSONObject(bodyStr)

                    // 1. Check for root "hls" M3U8 stream URL
                    val hlsUrl = json.optString("hls", "").trim()
                    if (hlsUrl.startsWith("http://") || hlsUrl.startsWith("https://")) {
                        AppLogger.d(TAG, "Piped [$cleanBaseUrl] found HLS stream for $youtubeId")
                        return@withTimeoutOrNull hlsUrl
                    }

                    if (isAudioOnly) {
                        val audioStreams = json.optJSONArray("audioStreams")
                        if (audioStreams != null && audioStreams.length() > 0) {
                            for (i in 0 until audioStreams.length()) {
                                val stream = audioStreams.getJSONObject(i)
                                val url = stream.optString("url", "").trim()
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    AppLogger.d(TAG, "Piped [$cleanBaseUrl] found audio stream for $youtubeId")
                                    return@withTimeoutOrNull url
                                }
                            }
                        }
                    } else {
                        val videoStreams = json.optJSONArray("videoStreams")
                        if (videoStreams != null && videoStreams.length() > 0) {
                            for (i in 0 until videoStreams.length()) {
                                val stream = videoStreams.getJSONObject(i)
                                val url = stream.optString("url", "").trim()
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    AppLogger.d(TAG, "Piped [$cleanBaseUrl] found video stream for $youtubeId")
                                    return@withTimeoutOrNull url
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error resolving via Piped instance [$baseUrl] for $youtubeId: ${e.message}")
                }
                null
            }
        }

    suspend fun resolveViaInvidious(youtubeId: String, baseUrl: String, isAudioOnly: Boolean): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(4000L) {
                try {
                    val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
                    val targetUrl = "$cleanBaseUrl/api/v1/videos/$youtubeId"
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "SNfetchPLAYER-App")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        AppLogger.w(TAG, "Invidious instance [$cleanBaseUrl] returned HTTP ${response.code} for $youtubeId")
                        return@withTimeoutOrNull null
                    }

                    val bodyStr = response.body?.string() ?: return@withTimeoutOrNull null
                    val json = JSONObject(bodyStr)

                    // 1. Try "formatStreams" first (combined video+audio)
                    val formatStreams = json.optJSONArray("formatStreams")
                    if (formatStreams != null && formatStreams.length() > 0) {
                        for (i in 0 until formatStreams.length()) {
                            val stream = formatStreams.getJSONObject(i)
                            val url = stream.optString("url", "").trim()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                AppLogger.d(TAG, "Invidious [$cleanBaseUrl] found formatStream for $youtubeId")
                                return@withTimeoutOrNull url
                            }
                        }
                    }

                    // 2. Try "adaptiveFormats"
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                        val targetType = if (isAudioOnly) "audio/" else "video/"
                        for (i in 0 until adaptiveFormats.length()) {
                            val stream = adaptiveFormats.getJSONObject(i)
                            val type = stream.optString("type", "").lowercase()
                            val url = stream.optString("url", "").trim()
                            if (type.contains(targetType) && (url.startsWith("http://") || url.startsWith("https://"))) {
                                AppLogger.d(TAG, "Invidious [$cleanBaseUrl] found adaptiveFormat ($type) for $youtubeId")
                                return@withTimeoutOrNull url
                            }
                        }

                        // 3. Fallback to any valid URL in adaptiveFormats
                        for (i in 0 until adaptiveFormats.length()) {
                            val stream = adaptiveFormats.getJSONObject(i)
                            val url = stream.optString("url", "").trim()
                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                AppLogger.d(TAG, "Invidious [$cleanBaseUrl] fallback adaptive format for $youtubeId")
                                return@withTimeoutOrNull url
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error resolving via Invidious instance [$baseUrl] for $youtubeId: ${e.message}")
                }
                null
            }
        }
}
