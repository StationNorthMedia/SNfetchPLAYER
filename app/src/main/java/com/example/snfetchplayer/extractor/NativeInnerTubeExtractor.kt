package com.example.snfetchplayer.extractor

import com.example.snfetchplayer.util.AppLogger
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object NativeInnerTubeExtractor {

    private const val TAG = "InnerTubeExtractor"
    private const val VISITOR_ID_ENDPOINT = "https://www.youtube.com/youtubei/v1/visitor_id"
    private const val PLAYER_ENDPOINT = "https://www.youtube.com/youtubei/v1/player"
    private const val USER_AGENT_ANDROID = "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip"
    private const val USER_AGENT_VR = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var cachedVisitorData: String? = null

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun getVisitorData(): String? = withContext(Dispatchers.IO) {
        cachedVisitorData?.let { return@withContext it }

        try {
            AppLogger.d(TAG, "Fetching fresh visitorData from YouTube InnerTube API...")
            val payload = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "ANDROID")
                        addProperty("clientVersion", "21.02.35")
                    })
                })
            }

            val request = Request.Builder()
                .url(VISITOR_ID_ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", USER_AGENT_ANDROID)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = response.body?.string()
                if (!json.isNullOrEmpty()) {
                    val root = JsonParser.parseString(json).asJsonObject
                    val responseContext = root.getAsJsonObject("responseContext")
                    val visitorData = if (responseContext != null && responseContext.has("visitorData")) {
                        val element = responseContext.get("visitorData")
                        if (element.isJsonObject) {
                            element.asJsonObject.get("visitorData")?.asString
                        } else if (element.isJsonPrimitive) {
                            element.asString
                        } else null
                    } else null

                    if (!visitorData.isNullOrEmpty()) {
                        cachedVisitorData = visitorData
                        AppLogger.d(TAG, "Acquired visitorData: ${visitorData.take(30)}...")
                        return@withContext visitorData
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch visitorData (${e.message})")
        }
        return@withContext null
    }

    suspend fun extractStreamUrl(youtubeId: String, isAudioOnly: Boolean): String? = withContext(Dispatchers.IO) {
        val visitorData = getVisitorData()
        AppLogger.d(TAG, "Extracting stream for [$youtubeId] (isAudioOnly=$isAudioOnly, target=720p HD)")

        // Primary: ANDROID client (returns HTTP 200 playable combined streams)
        val primaryResult = queryInnerTubePlayer(
            youtubeId = youtubeId,
            clientName = "ANDROID",
            clientVersion = "21.02.35",
            userAgent = USER_AGENT_ANDROID,
            visitorData = visitorData,
            isAudioOnly = isAudioOnly
        )

        if (!primaryResult.isNullOrEmpty()) {
            AppLogger.d(TAG, "SUCCESS (ANDROID Client) resolved stream for [$youtubeId]")
            return@withContext primaryResult
        }

        // Fallback: ANDROID_VR client
        AppLogger.d(TAG, "Primary ANDROID client failed, trying ANDROID_VR client fallback for [$youtubeId]...")
        val vrResult = queryInnerTubePlayer(
            youtubeId = youtubeId,
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            userAgent = USER_AGENT_VR,
            visitorData = visitorData,
            isAudioOnly = isAudioOnly
        )

        if (!vrResult.isNullOrEmpty()) {
            AppLogger.d(TAG, "SUCCESS (ANDROID_VR Client) resolved stream for [$youtubeId]")
            return@withContext vrResult
        }

        AppLogger.e(TAG, "All native InnerTube extraction attempts failed for [$youtubeId]")
        return@withContext null
    }

    private fun queryInnerTubePlayer(
        youtubeId: String,
        clientName: String,
        clientVersion: String,
        userAgent: String,
        visitorData: String?,
        isAudioOnly: Boolean
    ): String? {
        try {
            val clientObj = JsonObject().apply {
                addProperty("clientName", clientName)
                addProperty("clientVersion", clientVersion)
                addProperty("hl", "en")
                addProperty("gl", "US")
                if (clientName == "ANDROID") {
                    addProperty("androidSdkVersion", 30)
                    addProperty("osName", "Android")
                    addProperty("osVersion", "11")
                } else if (clientName == "ANDROID_VR") {
                    addProperty("deviceMake", "Oculus")
                    addProperty("deviceModel", "Quest 3")
                    addProperty("androidSdkVersion", 32)
                    addProperty("osName", "Android")
                    addProperty("osVersion", "12L")
                }
                if (!visitorData.isNullOrEmpty()) {
                    addProperty("visitorData", visitorData)
                }
            }

            val payload = JsonObject().apply {
                addProperty("videoId", youtubeId)
                add("context", JsonObject().apply {
                    add("client", clientObj)
                })
            }

            val requestBuilder = Request.Builder()
                .url(PLAYER_ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", userAgent)
                .header("Origin", "https://www.youtube.com")

            if (!visitorData.isNullOrEmpty()) {
                requestBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                AppLogger.d(TAG, "Client $clientName HTTP ${response.code} for [$youtubeId]")
                return null
            }

            val body = response.body?.string() ?: return null
            val root = JsonParser.parseString(body).asJsonObject

            val playability = root.getAsJsonObject("playabilityStatus")
            val status = playability?.get("status")?.asString
            if (status != "OK") {
                val reason = playability?.get("reason")?.asString ?: "Unknown reason"
                AppLogger.d(TAG, "Client $clientName playability status: $status ($reason) for [$youtubeId]")
                return null
            }

            val streamingData = root.getAsJsonObject("streamingData") ?: return null

            // 1. Check formats array (combined video + audio) - Prefer 720p (itag 22) first, then 360p (itag 18)
            val formats = streamingData.getAsJsonArray("formats")
            if (formats != null && formats.size() > 0) {
                // Pass 1: Look for 720p HD (itag 22 or qualityLabel 720p)
                for (i in 0 until formats.size()) {
                    val fmt = formats.get(i).asJsonObject
                    val url = fmt.get("url")?.asString
                    val itag = fmt.get("itag")?.asInt ?: 0
                    val quality = fmt.get("qualityLabel")?.asString ?: ""
                    if (!url.isNullOrEmpty() && (itag == 22 || quality.contains("720"))) {
                        AppLogger.d(TAG, "Selected 720p HD format itag=$itag for [$youtubeId]")
                        return url
                    }
                }

                // Pass 2: Fallback to any valid combined format (e.g. itag 18)
                for (i in 0 until formats.size()) {
                    val fmt = formats.get(i).asJsonObject
                    val url = fmt.get("url")?.asString
                    if (!url.isNullOrEmpty()) {
                        val itag = fmt.get("itag")?.asInt ?: 0
                        val quality = fmt.get("qualityLabel")?.asString ?: ""
                        AppLogger.d(TAG, "Selected fallback format itag=$itag ($quality) for [$youtubeId]")
                        return url
                    }
                }
            }

            // 2. Check adaptiveFormats array
            val adaptiveFormats = streamingData.getAsJsonArray("adaptiveFormats")
            if (adaptiveFormats != null && adaptiveFormats.size() > 0) {
                // For Audio-only mode, pick audio formats first
                if (isAudioOnly) {
                    for (i in 0 until adaptiveFormats.size()) {
                        val fmt = adaptiveFormats.get(i).asJsonObject
                        val url = fmt.get("url")?.asString
                        val mimeType = fmt.get("mimeType")?.asString ?: ""
                        if (!url.isNullOrEmpty() && mimeType.contains("audio/")) {
                            val itag = fmt.get("itag")?.asInt ?: 0
                            AppLogger.d(TAG, "Selected adaptive audio format itag=$itag, mime=$mimeType for [$youtubeId]")
                            return url
                        }
                    }
                }

                // Pass 1 for Video mode: Look for 720p adaptive video format
                for (i in 0 until adaptiveFormats.size()) {
                    val fmt = adaptiveFormats.get(i).asJsonObject
                    val url = fmt.get("url")?.asString
                    val quality = fmt.get("qualityLabel")?.asString ?: ""
                    if (!url.isNullOrEmpty() && quality.contains("720")) {
                        val itag = fmt.get("itag")?.asInt ?: 0
                        AppLogger.d(TAG, "Selected 720p adaptive video format itag=$itag for [$youtubeId]")
                        return url
                    }
                }

                // Pass 2 for Video mode: Any valid adaptive format
                for (i in 0 until adaptiveFormats.size()) {
                    val fmt = adaptiveFormats.get(i).asJsonObject
                    val url = fmt.get("url")?.asString
                    if (!url.isNullOrEmpty()) {
                        val itag = fmt.get("itag")?.asInt ?: 0
                        AppLogger.d(TAG, "Selected adaptive format itag=$itag for [$youtubeId]")
                        return url
                    }
                }
            }

            // 3. Check HLS manifest URL as fallback
            val hlsUrl = streamingData.get("hlsManifestUrl")?.asString
            if (!hlsUrl.isNullOrEmpty()) {
                AppLogger.d(TAG, "Selected HLS manifest URL for [$youtubeId]")
                return hlsUrl
            }

        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception in queryInnerTubePlayer ($clientName) for [$youtubeId]", e)
        }

        return null
    }
}
