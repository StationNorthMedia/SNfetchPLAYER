package net.sn.fetchplayer.manager

import android.content.Context
import net.sn.fetchplayer.extractor.ExternalApiExtractor
import net.sn.fetchplayer.extractor.NativeInnerTubeExtractor
import net.sn.fetchplayer.model.PlaybackMode
import net.sn.fetchplayer.model.Track
import net.sn.fetchplayer.util.AppLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class PlaylistManager(
    private val context: Context,
    private val stationContentManager: StationContentManager
) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val resolvedCache = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()
    private val historyStack = ArrayDeque<Track>()

    private var masterCatalog: List<RawPlaylistItem> = emptyList()
    private val currentShuffleList = mutableListOf<RawPlaylistItem>()
    private var shuffleIndex = 0
    private var globalTrackCounter = 0

    data class RawPlaylistItem(
        val youtubeId: String,
        val title: String,
        val artist: String
    ) : java.io.Serializable

    init {
        initMasterCatalog()
    }

    private fun initMasterCatalog() {
        try {
            val curatedTracks = CuratedCatalogManager(context).getCuratedTracks()
            if (curatedTracks.isNotEmpty()) {
                masterCatalog = curatedTracks
                AppLogger.d("PlaylistManager", "Loaded ${curatedTracks.size} tracks into masterCatalog from CuratedCatalogManager")
                reshuffleCatalog()
                return
            }
        } catch (e: Exception) {
            AppLogger.e("PlaylistManager", "Failed to load catalog via CuratedCatalogManager", e)
        }

        try {
            val jsonString = context.assets.open("us_rnb_top50.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<RawPlaylistItem>>() {}.type
            val items: List<RawPlaylistItem> = gson.fromJson(jsonString, type)
            if (items.isNotEmpty()) {
                masterCatalog = items
                AppLogger.d("PlaylistManager", "Loaded ${items.size} tracks from assets/us_rnb_top50.json")
                reshuffleCatalog()
                return
            }
        } catch (e: Exception) {
            AppLogger.e("PlaylistManager", "Failed to load us_rnb_top50.json from assets", e)
        }

        masterCatalog = listOf(
            RawPlaylistItem("GxBSyx85Kp8", "Yeah!", "Usher ft. Lil Jon, Ludacris"),
            RawPlaylistItem("o3IWTfcks4k", "U Got It Bad", "Usher"),
            RawPlaylistItem("t5XNWFw5HVw", "Burn", "Usher"),
            RawPlaylistItem("fPgf2meEX1w", "My Boo", "Usher ft. Alicia Keys"),
            RawPlaylistItem("YtC92pzp5vw", "Goodies", "Ciara ft. Petey Pablo")
        )
        reshuffleCatalog()
    }

    fun updateMasterCatalog(newCatalog: List<RawPlaylistItem>) {
        if (newCatalog.isNotEmpty()) {
            masterCatalog = newCatalog
            AppLogger.d("PlaylistManager", "Master catalog updated with ${newCatalog.size} items. Triggering reshuffle.")
            reshuffleCatalog()
        }
    }

    private fun reshuffleCatalog() {
        currentShuffleList.clear()
        currentShuffleList.addAll(masterCatalog)
        currentShuffleList.shuffle()
        shuffleIndex = 0
        AppLogger.d("PlaylistManager", "Reshuffled playlist catalog (${currentShuffleList.size} tracks)")
    }

    suspend fun getPreviousTrack(): Track? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (historyStack.size > 1) {
                historyStack.pop() // Remove current
                val prev = historyStack.peek()
                AppLogger.d("PlaylistManager", "Popped previous track from history: '${prev?.title}'")
                return@withContext prev
            }
        }
        return@withContext null
    }

    suspend fun getNextTrack(mode: PlaybackMode): Track = withContext(Dispatchers.IO) {
        mutex.withLock {
            globalTrackCounter++

            // Count how many consecutive YouTube tracks exist at the top of historyStack since the last Station Content
            var ytTracksSinceLastStation = 0
            for (pastTrack in historyStack) {
                if (pastTrack.isStationContent) {
                    break
                }
                ytTracksSinceLastStation++
            }

            AppLogger.d("PlaylistManager", "2:1 Rotation Check -> YouTube tracks since last Station Content: $ytTracksSinceLastStation")

            // Check if 2 YouTube tracks played since last Station Content -> insert Station Content (ID or Jingle)
            if (ytTracksSinceLastStation >= 2) {
                val stationTrack = stationContentManager.getStationContentForMode(mode)
                if (stationTrack != null) {
                    AppLogger.d("PlaylistManager", "ROTATION -> Inserting Station Content [${stationTrack.title}] for mode $mode")
                    historyStack.push(stationTrack)
                    return@withContext stationTrack
                } else {
                    AppLogger.d("PlaylistManager", "ROTATION -> Station Content unavailable/placeholder. Skipping directly to YouTube track.")
                }
            }

            // Pick next YouTube track
            var attempts = 0
            while (attempts < 10) {
                attempts++
                if (shuffleIndex >= currentShuffleList.size) {
                    reshuffleCatalog()
                }

                val rawItem = currentShuffleList[shuffleIndex]
                shuffleIndex++

                AppLogger.d("PlaylistManager", "Resolving track #$globalTrackCounter (Attempt $attempts): '${rawItem.artist} - ${rawItem.title}' [${rawItem.youtubeId}]")

                val isAudioOnly = (mode == PlaybackMode.SN_RADIO)
                val resolvedUrl = resolveStreamUri(rawItem.youtubeId, isAudioOnly)

                if (!resolvedUrl.isNullOrEmpty()) {
                    AppLogger.d("PlaylistManager", "SUCCESS resolved stream URL for [${rawItem.youtubeId}]")
                    val track = Track(
                        id = rawItem.youtubeId + "_" + globalTrackCounter,
                        title = rawItem.title,
                        artist = rawItem.artist,
                        youtubeId = rawItem.youtubeId,
                        resolvedStreamUrl = resolvedUrl,
                        isStationContent = false,
                        isLocalVideo = false,
                        localResourceUri = null
                    )
                    historyStack.push(track)
                    return@withContext track
                } else {
                    AppLogger.d("PlaylistManager", "Failed to resolve stream for [${rawItem.youtubeId}]")
                }
            }

            AppLogger.e("PlaylistManager", "All resolution attempts failed.")
            val fallbackTrack = Track(
                id = "fallback_$globalTrackCounter",
                title = "Station North Stream Fallback",
                artist = "Station North Network",
                resolvedStreamUrl = null,
                isStationContent = false,
                isLocalVideo = false,
                localResourceUri = null
            )
            return@withContext fallbackTrack
        }
    }

    suspend fun prefetchNextTracks(mode: PlaybackMode, count: Int = 2) = withContext(Dispatchers.IO) {
        mutex.withLock {
            var tempIndex = shuffleIndex
            val isAudioOnly = (mode == PlaybackMode.SN_RADIO)
            for (i in 1..count) {
                if (tempIndex < currentShuffleList.size) {
                    val item = currentShuffleList[tempIndex]
                    tempIndex++
                    AppLogger.d("PlaylistManager", "Prefetching stream for [${item.youtubeId}]")
                    resolveStreamUri(item.youtubeId, isAudioOnly)
                }
            }
        }
    }

    suspend fun resolveTrack(rawItem: RawPlaylistItem, mode: PlaybackMode): Track? = withContext(Dispatchers.IO) {
        val isAudioOnly = (mode == PlaybackMode.SN_RADIO)
        val resolvedUrl = resolveStreamUri(rawItem.youtubeId, isAudioOnly)
        if (!resolvedUrl.isNullOrEmpty()) {
            return@withContext Track(
                id = rawItem.youtubeId + "_curator_" + System.currentTimeMillis(),
                title = rawItem.title,
                artist = rawItem.artist,
                youtubeId = rawItem.youtubeId,
                resolvedStreamUrl = resolvedUrl,
                isStationContent = false,
                isLocalVideo = false,
                localResourceUri = null
            )
        }
        return@withContext null
    }

    suspend fun resolveStreamUri(youtubeId: String, isAudioOnly: Boolean): String? {
        val mode = SettingsManager.getExtractionMode(context)
        val targetQuality = if (isAudioOnly) "auto" else SettingsManager.getVideoQuality(context)
        val cacheKey = "${youtubeId}_${mode}_${if (isAudioOnly) "AUDIO" else "VIDEO_${targetQuality}"}"
        resolvedCache[cacheKey]?.let {
            AppLogger.d("PlaylistManager", "Using cached stream URL for $youtubeId ($cacheKey)")
            return it
        }

        val tryTier1 = (mode == "auto")
        val tryTier2 = (mode == "auto" || mode == "piped" || mode == "external")
        val tryTier3 = (mode == "auto" || mode == "invidious" || mode == "external")

        AppLogger.d("PlaylistManager", "Stream resolution for $youtubeId [Mode: $mode | T1:$tryTier1, T2:$tryTier2, T3:$tryTier3]")

        // Tier 1: Native InnerTube Extractor
        if (tryTier1) {
            val resolvedUrl = NativeInnerTubeExtractor.extractStreamUrl(youtubeId, isAudioOnly, targetQuality)
            if (!resolvedUrl.isNullOrEmpty()) {
                AppLogger.d("PlaylistManager", "Tier 1 SUCCESS via Native InnerTube")
                resolvedCache[cacheKey] = resolvedUrl
                return resolvedUrl
            }
        }

        // Tier 2: Decentralized Piped Extractor Instances
        if (tryTier2) {
            val customUrl = SettingsManager.getCustomExtractorUrl(context)
            val pipedInstances = if (customUrl.isNotEmpty()) (listOf(customUrl) + RemoteConfigManager.getPipedInstances()).distinct() else RemoteConfigManager.getPipedInstances()
            for (instance in pipedInstances) {
                AppLogger.d("PlaylistManager", "Tier 2 Extractor: Trying Piped instance [$instance] for $youtubeId")
                val pipedUrl = ExternalApiExtractor.resolveViaPiped(youtubeId, instance, isAudioOnly)
                if (!pipedUrl.isNullOrEmpty()) {
                    AppLogger.d("PlaylistManager", "Tier 2 SUCCESS via Piped [$instance]")
                    resolvedCache[cacheKey] = pipedUrl
                    return pipedUrl
                }
            }
        }

        // Tier 3: Decentralized Invidious Extractor Instances (with Live Registry Health Filter)
        if (tryTier3) {
            val customUrl = SettingsManager.getCustomExtractorUrl(context)
            val invidiousInstances = if (customUrl.isNotEmpty()) (listOf(customUrl) + RemoteConfigManager.getInvidiousInstances()).distinct() else RemoteConfigManager.getInvidiousInstances()
            for (instance in invidiousInstances) {
                AppLogger.d("PlaylistManager", "Tier 3 Extractor: Trying Invidious instance [$instance] for $youtubeId")
                val invidiousUrl = ExternalApiExtractor.resolveViaInvidious(youtubeId, instance, isAudioOnly)
                if (!invidiousUrl.isNullOrEmpty()) {
                    AppLogger.d("PlaylistManager", "Tier 3 SUCCESS via Invidious [$instance]")
                    resolvedCache[cacheKey] = invidiousUrl
                    return invidiousUrl
                }
            }
        }

        // Tier 4: Cobalt Media Downloader / Private API Rest Extractor
        if (mode == "auto" || mode == "external") {
            val cobaltInstances = RemoteConfigManager.getCobaltInstances()
            for (instance in cobaltInstances) {
                AppLogger.d("PlaylistManager", "Tier 4 Extractor: Trying Cobalt API [$instance] for $youtubeId")
                val cobaltUrl = ExternalApiExtractor.resolveViaCobalt(youtubeId, instance, isAudioOnly)
                if (!cobaltUrl.isNullOrEmpty()) {
                    AppLogger.d("PlaylistManager", "Tier 4 SUCCESS via Cobalt [$instance]")
                    resolvedCache[cacheKey] = cobaltUrl
                    return cobaltUrl
                }
            }
        }

        return null
    }
}
