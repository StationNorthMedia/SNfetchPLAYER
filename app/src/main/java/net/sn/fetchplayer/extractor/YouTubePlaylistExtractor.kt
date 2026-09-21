package net.sn.fetchplayer.extractor

import net.sn.fetchplayer.manager.PlaylistManager.RawPlaylistItem
import net.sn.fetchplayer.util.AppLogger
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object YouTubePlaylistExtractor {

    private const val TAG = "PlaylistExtractor"
    private const val BROWSE_ENDPOINT = "https://www.youtube.com/youtubei/v1/browse"
    private const val RESOLVE_URL_ENDPOINT = "https://www.youtube.com/youtubei/v1/navigation/resolve_url"
    private const val USER_AGENT_ANDROID = "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val isNewPipeInitialized = AtomicBoolean(false)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val fastHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private fun ensureNewPipeInitialized() {
        if (isNewPipeInitialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(NewPipeDownloader(httpClient))
                AppLogger.d(TAG, "NewPipeExtractor initialized successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize NewPipeExtractor: ${e.message}", e)
            }
        }
    }

    suspend fun fetchPlaylistTracks(playlistInput: String): List<RawPlaylistItem> = withContext(Dispatchers.IO) {
        val trimmed = playlistInput.trim()
        AppLogger.d(TAG, "Fetching tracks for input: '$trimmed'")

        // Method 1: Check Python Resolver Server (Port 8080) with 1s fast timeout
        val serverItems = fetchViaResolverServer(trimmed)
        if (serverItems.isNotEmpty()) {
            val filtered = filterShorts(serverItems)
            AppLogger.d(TAG, "SUCCESS Resolver Server extracted ${filtered.size} non-short tracks for '$trimmed'")
            return@withContext filtered
        }

        // Method 2: Direct InnerTube Browse & Continuation API (Multi-page + exact title/artist)
        val innerTubeItems = fetchViaInnerTube(trimmed)
        if (innerTubeItems.isNotEmpty()) {
            val filtered = filterShorts(innerTubeItems)
            AppLogger.d(TAG, "SUCCESS InnerTube extracted ${filtered.size} non-short tracks for '$trimmed'")
            return@withContext filtered
        }

        // Method 3: NewPipeExtractor Fallback
        val newPipeItems = fetchViaNewPipe(trimmed)
        if (newPipeItems.isNotEmpty()) {
            val filtered = filterShorts(newPipeItems)
            AppLogger.d(TAG, "SUCCESS NewPipe extracted ${filtered.size} non-short tracks for '$trimmed'")
            return@withContext filtered
        }

        AppLogger.e(TAG, "Failed to extract tracks for input '$trimmed' via all methods.")
        return@withContext emptyList()
    }

    private fun filterShorts(items: List<RawPlaylistItem>): List<RawPlaylistItem> {
        return items.filter { item ->
            val titleLower = item.title.lowercase()
            !titleLower.contains("#shorts") && !titleLower.contains("#short")
        }
    }

    private fun fetchViaResolverServer(inputUrl: String): List<RawPlaylistItem> {
        val endpoints = listOf(
            "http://10.0.2.2:8080/fetch_playlist",
            "http://127.0.0.1:8080/fetch_playlist",
            "http://localhost:8080/fetch_playlist"
        )
        for (baseEndpoint in endpoints) {
            try {
                val httpUrl = baseEndpoint.toHttpUrlOrNull()?.newBuilder()
                    ?.addQueryParameter("url", inputUrl)
                    ?.build() ?: continue

                val request = Request.Builder()
                    .url(httpUrl)
                    .get()
                    .build()

                val response = fastHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: continue
                    val root = JsonParser.parseString(jsonStr).asJsonObject
                    if (root.get("status")?.asString == "success") {
                        val tracksArray = root.getAsJsonArray("tracks") ?: continue
                        val list = mutableListOf<RawPlaylistItem>()
                        for (i in 0 until tracksArray.size()) {
                            val obj = tracksArray.get(i).asJsonObject
                            val ytId = obj.get("youtubeId")?.asString ?: continue
                            val title = obj.get("title")?.asString ?: "Unknown Title"
                            val artist = obj.get("artist")?.asString ?: "Unknown Artist"
                            list.add(RawPlaylistItem(ytId, title, artist))
                        }
                        if (list.isNotEmpty()) return list
                    }
                }
            } catch (_: Exception) {}
        }
        return emptyList()
    }

    private fun fetchViaInnerTube(input: String): List<RawPlaylistItem> {
        try {
            val fullUrl = buildFullYouTubeUrl(input)
            var playlistId = extractPlaylistIdFromUrl(fullUrl)

            if (playlistId.isNullOrEmpty() && (isChannelUrl(fullUrl) || input.startsWith("@"))) {
                playlistId = resolveChannelUploadsPlaylistId(fullUrl)
            }

            if (!playlistId.isNullOrEmpty()) {
                val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
                return queryInnerTubeBrowseWithContinuation(browseId)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception in fetchViaInnerTube: ${e.message}", e)
        }
        return emptyList()
    }

    private fun resolveChannelUploadsPlaylistId(channelUrl: String): String? {
        try {
            AppLogger.d(TAG, "Resolving channel handle/URL to channel ID: $channelUrl")
            val payload = JsonObject().apply {
                addProperty("url", channelUrl)
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "ANDROID")
                        addProperty("clientVersion", "21.02.35")
                        addProperty("hl", "en")
                        addProperty("gl", "US")
                    })
                })
            }

            val request = Request.Builder()
                .url(RESOLVE_URL_ENDPOINT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .header("User-Agent", USER_AGENT_ANDROID)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return null
                val root = JsonParser.parseString(jsonStr).asJsonObject
                val endpoint = root.getAsJsonObject("endpoint")
                val browseEndpoint = endpoint?.getAsJsonObject("browseEndpoint")
                val channelBrowseId = browseEndpoint?.get("browseId")?.asString

                if (!channelBrowseId.isNullOrEmpty()) {
                    if (channelBrowseId.startsWith("UC")) {
                        val uploadsId = "UU" + channelBrowseId.substring(2)
                        AppLogger.d(TAG, "Resolved Channel $channelBrowseId to Uploads Playlist: $uploadsId")
                        return uploadsId
                    }
                    return channelBrowseId
                }
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "Failed to resolve channel uploads playlist ID for $channelUrl: ${e.message}")
        }
        return null
    }

    private fun queryInnerTubeBrowseWithContinuation(browseId: String, maxPages: Int = 10): List<RawPlaylistItem> {
        val allTracks = mutableListOf<RawPlaylistItem>()
        val seenIds = mutableSetOf<String>()
        var continuationToken: String? = null

        for (page in 1..maxPages) {
            try {
                val payload = JsonObject().apply {
                    if (continuationToken != null) {
                        addProperty("continuation", continuationToken)
                    } else {
                        addProperty("browseId", browseId)
                    }
                    add("context", JsonObject().apply {
                        add("client", JsonObject().apply {
                            addProperty("clientName", "ANDROID")
                            addProperty("clientVersion", "21.02.35")
                            addProperty("hl", "en")
                            addProperty("gl", "US")
                        })
                    })
                }

                val request = Request.Builder()
                    .url(BROWSE_ENDPOINT)
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", USER_AGENT_ANDROID)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) break

                val jsonStr = response.body?.string() ?: break
                val root = JsonParser.parseString(jsonStr)

                val (pageTracks, nextToken) = parseInnerTubePage(root, seenIds)
                allTracks.addAll(pageTracks)

                AppLogger.d(TAG, "Page $page: Extracted ${pageTracks.size} new tracks (Total: ${allTracks.size}). Next token: ${nextToken != null}")

                if (!nextToken.isNullOrEmpty() && nextToken != continuationToken) {
                    continuationToken = nextToken
                } else {
                    break
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error fetching page $page for $browseId: ${e.message}")
                break
            }
        }

        AppLogger.d(TAG, "InnerTube multi-page browse completed: ${allTracks.size} total tracks extracted")
        return allTracks
    }

    private data class PageParseResult(
        val tracks: List<RawPlaylistItem>,
        val nextContinuationToken: String?
    )

    private fun parseInnerTubePage(jsonElement: JsonElement, seenIds: MutableSet<String>): PageParseResult {
        val tracks = mutableListOf<RawPlaylistItem>()
        var nextToken: String? = null

        fun walk(element: JsonElement) {
            if (element.isJsonObject) {
                val obj = element.asJsonObject

                // Extract continuation token if available
                if (obj.has("continuationCommand") && obj.get("continuationCommand").isJsonObject) {
                    val tok = obj.getAsJsonObject("continuationCommand").get("token")?.asString
                    if (!tok.isNullOrEmpty()) nextToken = tok
                } else if (obj.has("continuationItemRenderer") && obj.get("continuationItemRenderer").isJsonObject) {
                    val cir = obj.getAsJsonObject("continuationItemRenderer")
                    val tok = cir.getAsJsonObject("continuationEndpoint")
                        ?.getAsJsonObject("continuationCommand")?.get("token")?.asString
                    if (!tok.isNullOrEmpty()) nextToken = tok
                }

                // Check for Shorts indicators
                val isShort = obj.has("shortsLockupViewModel") || obj.has("reelWatchEndpoint")

                // Case A: videoWithContextModel (New Android Element format)
                val vwcm = obj.getAsJsonObject("videoWithContextModel")
                if (vwcm != null && !isShort) {
                    val vwcd = vwcm.getAsJsonObject("videoWithContextData")
                    if (vwcd != null) {
                        val vdata = vwcd.getAsJsonObject("videoData")
                        val lockup = vdata?.getAsJsonObject("lockupMetadata")?.getAsJsonObject("lockupMetadataViewModel")

                        val title = lockup?.getAsJsonObject("title")?.get("content")?.asString
                        var artist: String? = null
                        val rows = lockup?.getAsJsonObject("metadata")?.getAsJsonObject("contentMetadataViewModel")?.getAsJsonArray("metadataRows")
                        if (rows != null && rows.size() > 0) {
                            val parts = rows.get(0).asJsonObject.getAsJsonArray("metadataParts")
                            if (parts != null && parts.size() > 0) {
                                artist = parts.get(0).asJsonObject.getAsJsonObject("text")?.get("content")?.asString
                            }
                        }

                        val vid = vwcd.getAsJsonObject("onTap")?.getAsJsonObject("innertubeCommand")?.getAsJsonObject("watchEndpoint")?.get("videoId")?.asString

                        if (!vid.isNullOrEmpty() && seenIds.add(vid)) {
                            val finalTitle = title ?: "YouTube Track"
                            val finalArtist = artist ?: "Station North Music"
                            if (!finalTitle.lowercase().contains("#shorts") && !finalTitle.lowercase().contains("#short")) {
                                tracks.add(RawPlaylistItem(youtubeId = vid, title = finalTitle, artist = finalArtist))
                            }
                        }
                    }
                }

                // Case B: Standard playlistVideoRenderer, gridVideoRenderer, videoRenderer
                val vr = obj.getAsJsonObject("playlistVideoRenderer")
                    ?: obj.getAsJsonObject("gridVideoRenderer")
                    ?: obj.getAsJsonObject("videoRenderer")
                    ?: obj.getAsJsonObject("compactVideoRenderer")

                if (vr != null && !isShort) {
                    val vid = vr.get("videoId")?.asString
                    if (!vid.isNullOrEmpty() && seenIds.add(vid)) {
                        val title = extractText(vr.get("title")) ?: "YouTube Track"
                        val artist = extractText(vr.get("shortBylineText"))
                            ?: extractText(vr.get("longBylineText"))
                            ?: extractText(vr.get("ownerText"))
                            ?: "Station North Music"
                        if (!title.lowercase().contains("#shorts") && !title.lowercase().contains("#short")) {
                            tracks.add(RawPlaylistItem(youtubeId = vid, title = title, artist = artist))
                        }
                    }
                }

                // Recurse into children
                for ((_, v) in obj.entrySet()) {
                    walk(v)
                }
            } else if (element.isJsonArray) {
                for (item in element.asJsonArray) {
                    walk(item)
                }
            }
        }

        walk(jsonElement)
        return PageParseResult(tracks, nextToken)
    }

    private fun extractText(jsonElement: JsonElement?): String? {
        if (jsonElement == null || jsonElement.isJsonNull) return null
        if (jsonElement.isJsonPrimitive) return jsonElement.asString
        if (jsonElement.isJsonObject) {
            val obj = jsonElement.asJsonObject
            if (obj.has("runs") && obj.get("runs").isJsonArray) {
                val runs = obj.getAsJsonArray("runs")
                if (runs.size() > 0 && runs.get(0).isJsonObject) {
                    return runs.get(0).asJsonObject.get("text")?.asString
                }
            }
            if (obj.has("simpleText") && obj.get("simpleText").isJsonPrimitive) {
                return obj.get("simpleText").asString
            }
        }
        return null
    }

    private fun fetchViaNewPipe(input: String): List<RawPlaylistItem> {
        val results = mutableListOf<RawPlaylistItem>()
        try {
            ensureNewPipeInitialized()
            val fullUrl = buildFullYouTubeUrl(input)

            val targetPlaylistUrl = if (isChannelUrl(fullUrl) || input.startsWith("@")) {
                val uploadsPlaylistId = resolveChannelUploadsPlaylistId(fullUrl)
                if (!uploadsPlaylistId.isNullOrEmpty()) {
                    "https://www.youtube.com/playlist?list=$uploadsPlaylistId"
                } else {
                    fullUrl
                }
            } else {
                fullUrl
            }

            AppLogger.d(TAG, "NewPipe parsing Playlist URL: $targetPlaylistUrl")
            val playlistExtractor = ServiceList.YouTube.getPlaylistExtractor(targetPlaylistUrl)
            playlistExtractor.fetchPage()
            val items = playlistExtractor.initialPage.items
            for (item in items) {
                val ytId = extractYoutubeIdFromUrl(item.url)
                if (!ytId.isNullOrEmpty()) {
                    results.add(RawPlaylistItem(ytId, item.name ?: "Unknown Title", item.uploaderName ?: "Unknown Artist"))
                }
            }
        } catch (e: Exception) {
            AppLogger.d(TAG, "NewPipeExtractor exception: ${e.message}")
        }
        return results
    }

    private fun buildFullYouTubeUrl(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.startsWith("@") -> "https://www.youtube.com/$input"
            input.startsWith("PL") || input.startsWith("UU") || input.startsWith("FL") -> "https://www.youtube.com/playlist?list=$input"
            else -> "https://www.youtube.com/$input"
        }
    }

    private fun isChannelUrl(url: String): Boolean {
        return url.contains("/@") || url.contains("/channel/") || url.contains("/c/") || url.contains("/user/")
    }

    private fun extractPlaylistIdFromUrl(url: String): String? {
        if (url.contains("list=")) {
            return url.substringAfter("list=").substringBefore("&")
        }
        return null
    }

    private fun extractYoutubeIdFromUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        if (url.contains("v=")) {
            return url.substringAfter("v=").substringBefore("&")
        }
        if (url.contains("youtu.be/")) {
            return url.substringAfter("youtu.be/").substringBefore("?")
        }
        return null
    }
}
