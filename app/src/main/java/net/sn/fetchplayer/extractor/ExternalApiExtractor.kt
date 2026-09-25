package net.sn.fetchplayer.extractor

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.sn.fetchplayer.util.AppLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ExternalApiExtractor {

    private const val TAG = "ExternalApiExtractor"

    // Obfuscated official Station North endpoints to prevent GitHub scraping
    // Base64 encoded: "https://stream-a.station-north.net" & "https://stream-b.station-north.net"
    private const val OBFUSCATED_EP_A = "aHR0cHM6Ly9zdHJlYW0tYS5zdGF0aW9uLW5vcnRoLm5ldA=="
    private const val OBFUSCATED_EP_B = "aHR0cHM6Ly9zdHJlYW0tYi5zdGF0aW9uLW5vcnRoLm5ldA=="

    fun getStationNorthEndpoints(): List<String> {
        return try {
            val epA = String(Base64.decode(OBFUSCATED_EP_A, Base64.DEFAULT), Charsets.UTF_8).trim()
            val epB = String(Base64.decode(OBFUSCATED_EP_B, Base64.DEFAULT), Charsets.UTF_8).trim()
            listOf(epB, epA) // Try stream-b first, then stream-a
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error decoding official endpoints: ${e.message}")
            emptyList()
        }
    }

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

    suspend fun resolveViaCobalt(youtubeId: String, baseUrl: String, isAudioOnly: Boolean): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(5000L) {
                try {
                    val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
                    val targetUrl = if (cleanBaseUrl.endsWith("/api")) cleanBaseUrl else "$cleanBaseUrl/"
                    val payload = JSONObject().apply {
                        put("url", "https://www.youtube.com/watch?v=$youtubeId")
                        put("downloadMode", if (isAudioOnly) "audio" else "auto")
                        if (isAudioOnly) {
                            put("audioFormat", "mp3")
                        }
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(mediaType))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        AppLogger.w(TAG, "Cobalt instance [$cleanBaseUrl] returned HTTP ${response.code} for $youtubeId")
                        return@withTimeoutOrNull null
                    }

                    val bodyStr = response.body?.string() ?: return@withTimeoutOrNull null
                    val json = JSONObject(bodyStr)
                    val streamUrl = json.optString("url", "").trim()
                    if (streamUrl.startsWith("http://") || streamUrl.startsWith("https://")) {
                        AppLogger.d(TAG, "Cobalt [$cleanBaseUrl] SUCCESS for $youtubeId")
                        return@withTimeoutOrNull streamUrl
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error resolving via Cobalt [$baseUrl] for $youtubeId: ${e.message}")
                }
                null
            }
        }

    suspend fun resolveViaStationNorthApi(youtubeId: String, baseUrl: String, isAudioOnly: Boolean): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(6000L) {
                try {
                    val cleanBaseUrl = baseUrl.trim().removeSuffix("/")
                    val targetUrl = "$cleanBaseUrl/api/extract?video_id=$youtubeId&is_audio=$isAudioOnly"
                    val request = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "SNfetchPLAYER-App")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        if (!bodyStr.isNullOrEmpty()) {
                            val json = JSONObject(bodyStr)
                            val status = json.optString("status", "")
                            val url = json.optString("url", "").trim()
                            if (status == "success" && (url.startsWith("http://") || url.startsWith("https://"))) {
                                AppLogger.d(TAG, "StationNorth Private Extractor [$cleanBaseUrl] SUCCESS for $youtubeId")
                                return@withTimeoutOrNull url
                            }
                        }
                    } else {
                        // Fallback to /api/stream
                        val fallbackUrl = "$cleanBaseUrl/api/stream?video_id=$youtubeId&is_audio=$isAudioOnly"
                        val reqFallback = Request.Builder()
                            .url(fallbackUrl)
                            .header("User-Agent", "SNfetchPLAYER-App")
                            .build()
                        val respFallback = httpClient.newCall(reqFallback).execute()
                        if (respFallback.isSuccessful) {
                            val bodyStr = respFallback.body?.string()
                            if (!bodyStr.isNullOrEmpty()) {
                                val json = JSONObject(bodyStr)
                                val status = json.optString("status", "")
                                val url = json.optString("url", "").trim()
                                if (status == "success" && (url.startsWith("http://") || url.startsWith("https://"))) {
                                    AppLogger.d(TAG, "StationNorth Private Extractor [$cleanBaseUrl] (/api/stream) SUCCESS for $youtubeId")
                                    return@withTimeoutOrNull url
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Error resolving via StationNorth Private API [$baseUrl] for $youtubeId: ${e.message}")
                }
                null
            }
        }

    // Health check benchmark helper for Settings Hub "Test Connection" buttons
    suspend fun testConnection(sourceType: String, customUrl: String = ""): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val testVideoId = "GxBSyx85Kp8"
            try {
                val url = when (sourceType) {
                    "innertube" -> NativeInnerTubeExtractor.extractStreamUrl(testVideoId, isAudioOnly = true)
                    "station_north" -> {
                        val endpoints = getStationNorthEndpoints()
                        var result: String? = null
                        for (ep in endpoints) {
                            result = resolveViaStationNorthApi(testVideoId, ep, isAudioOnly = true)
                            if (!result.isNullOrEmpty()) break
                        }
                        result
                    }
                    "ytdlp_api" -> {
                        val target = customUrl.ifEmpty { getStationNorthEndpoints().firstOrNull() ?: "" }
                        if (target.isEmpty()) null else resolveViaStationNorthApi(testVideoId, target, isAudioOnly = true)
                    }
                    "invidious" -> {
                        val target = customUrl.ifEmpty { "https://yewtu.be" }
                        resolveViaInvidious(testVideoId, target, isAudioOnly = true)
                    }
                    "cobalt" -> {
                        val target = customUrl.ifEmpty { "https://api.cobalt.tools" }
                        resolveViaCobalt(testVideoId, target, isAudioOnly = true)
                    }
                    "piped" -> {
                        val target = customUrl.ifEmpty { "https://pipedapi.kavin.rocks" }
                        resolveViaPiped(testVideoId, target, isAudioOnly = true)
                    }
                    else -> null
                }

                val latency = System.currentTimeMillis() - startTime
                if (!url.isNullOrEmpty()) {
                    Pair(true, "🟢 Connected (${latency}ms)")
                } else {
                    Pair(false, "🔴 Connection Failed")
                }
            } catch (e: Exception) {
                Pair(false, "🔴 Error: ${e.message}")
            }
        }
}
