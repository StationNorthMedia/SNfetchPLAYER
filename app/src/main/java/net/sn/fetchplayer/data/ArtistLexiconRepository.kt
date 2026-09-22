package net.sn.fetchplayer.data

import android.content.Context
import net.sn.fetchplayer.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

enum class LexiconSuggestionType {
    ARTIST,
    ALBUM,
    TRACK
}

data class LexiconAlbum(
    val title: String,
    val year: String,
    val type: String,
    val tracks: List<String>
)

data class LexiconArtist(
    val id: String,
    val name: String,
    val qid: String,
    val bio: String,
    val genre: String = "R&B / Urban",
    val discography: List<LexiconAlbum> = emptyList(),
    val relatedArtists: List<LexiconRelatedArtist> = emptyList()
)

data class LexiconRelatedArtist(
    val id: String,
    val name: String
)

data class LexiconSearchSuggestion(
    val type: LexiconSuggestionType,
    val title: String,
    val badge: String,
    val artist: LexiconArtist,
    val albumTitle: String = "",
    val trackName: String = ""
)

object ArtistLexiconRepository {

    private const val TAG = "ArtistLexiconRepository"
    private val artistMap = ConcurrentHashMap<String, LexiconArtist>()
    private val artistList = CopyOnWriteArrayList<LexiconArtist>()

    @Volatile
    private var isInitialized = false
    @Volatile
    private var isInitializing = false

    fun init(context: Context) {
        if (isInitialized || isInitializing) return
        isInitializing = true

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            loadLexiconInternal(context.applicationContext)
        }
    }

    suspend fun ensureLoaded(context: Context) = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            loadLexiconInternal(context.applicationContext)
        }
    }

    private fun loadLexiconInternal(context: Context) {
        try {
            AppLogger.d(TAG, "Loading sn_lexicon.json from assets...")
            val jsonStr = context.assets.open("sn_lexicon.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonStr)

            val tempArtists = mutableListOf<LexiconArtist>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "")
                val name = obj.optString("name", "").trim()
                val qid = obj.optString("qid", "").trim()
                val bio = obj.optString("bio", "").trim()
                val genre = obj.optString("genre", "R&B / Urban").ifEmpty { "R&B / Urban" }

                val discoArray = obj.optJSONArray("discography")
                val albums = mutableListOf<LexiconAlbum>()

                if (discoArray != null) {
                    for (j in 0 until discoArray.length()) {
                        val albObj = discoArray.getJSONObject(j)
                        val albTitle = albObj.optString("title", "").trim()
                        val year = albObj.optString("year", "").trim()
                        val recType = albObj.optString("type", "").trim()
                        val trArray = albObj.optJSONArray("tracks")
                        val tracks = mutableListOf<String>()

                        if (trArray != null) {
                            for (k in 0 until trArray.length()) {
                                val trName = trArray.getString(k).trim()
                                if (trName.isNotEmpty()) {
                                    tracks.add(trName)
                                }
                            }
                        }

                        if (albTitle.isNotEmpty() || tracks.isNotEmpty()) {
                            albums.add(LexiconAlbum(title = albTitle, year = year, type = recType, tracks = tracks))
                        }
                    }
                }

                val relatedArray = obj.optJSONArray("related_artists")
                val related = mutableListOf<LexiconRelatedArtist>()
                if (relatedArray != null) {
                    for (r in 0 until relatedArray.length()) {
                        val relObj = relatedArray.getJSONObject(r)
                        val relId = relObj.optString("id", "").trim()
                        val relName = relObj.optString("name", "").trim()
                        if (relId.isNotEmpty() || relName.isNotEmpty()) {
                            related.add(LexiconRelatedArtist(id = relId, name = relName))
                        }
                    }
                }

                if (name.isNotEmpty()) {
                    val artist = LexiconArtist(
                        id = id,
                        name = name,
                        qid = qid,
                        bio = bio,
                        genre = genre,
                        discography = albums,
                        relatedArtists = related
                    )
                    val key1 = normalizeKey(name)
                    val key2 = normalizeKeyAdvanced(name)
                    artistMap[key1] = artist
                    artistMap[key2] = artist
                    if (id.isNotEmpty()) {
                        artistMap[id] = artist
                        artistMap[normalizeKeyAdvanced(id)] = artist
                    }
                    tempArtists.add(artist)
                }
            }

            tempArtists.sortBy { it.name.lowercase() }
            artistList.clear()
            artistList.addAll(tempArtists)

            isInitialized = true
            AppLogger.d(TAG, "Successfully loaded ${artistList.size} artists into lightweight Lexicon repository.")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error loading sn_lexicon.json from assets", e)
        } finally {
            isInitializing = false
        }
    }

    fun getRandomArtist(): LexiconArtist? {
        if (artistList.isEmpty()) return null
        val candidates = artistList.filter { it.discography.isNotEmpty() }
        val pool = if (candidates.isNotEmpty()) candidates else artistList
        return pool[Random.nextInt(pool.size)]
    }

    fun findArtist(rawName: String): LexiconArtist? {
        val cleanName = cleanArtistName(rawName)
        if (cleanName.isEmpty()) return null

        val keyAdv = normalizeKeyAdvanced(cleanName)
        artistMap[keyAdv]?.let { return it }

        val keyNorm = normalizeKey(cleanName)
        artistMap[keyNorm]?.let { return it }

        val normalizedRawAdv = normalizeKeyAdvanced(rawName)
        artistMap[normalizedRawAdv]?.let { return it }

        val normalizedRaw = normalizeKey(rawName)
        artistMap[normalizedRaw]?.let { return it }

        if (keyAdv.length >= 3) {
            artistList.firstOrNull { artist ->
                val artistAdv = normalizeKeyAdvanced(artist.name)
                artistAdv == keyAdv || artistAdv.contains(keyAdv) || keyAdv.contains(artistAdv)
            }?.let { return it }
        }

        return null
    }

    fun findArtistById(id: String): LexiconArtist? {
        if (id.isEmpty()) return null
        return artistMap[id] ?: artistList.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    fun searchSuggestions(rawQuery: String, maxResults: Int = 12): List<LexiconSearchSuggestion> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty() || artistList.isEmpty()) return emptyList()

        val normQuery = normalizeKeyAdvanced(query)
        val results = mutableListOf<LexiconSearchSuggestion>()

        // 1. Check Artist Name Matches (Exact & Normalized Substring)
        for (artist in artistList) {
            if (results.size >= maxResults) break

            val cleanArtistName = cleanArtistName(artist.name)
            val cleanArtistLower = cleanArtistName.lowercase()
            val rawNameLower = artist.name.lowercase()
            val normArtist = normalizeKeyAdvanced(artist.name)

            val isMatch = cleanArtistLower.contains(query) ||
                    rawNameLower.contains(query) ||
                    (normQuery.isNotEmpty() && normArtist.contains(normQuery)) ||
                    (normQuery.isNotEmpty() && normQuery.contains(normArtist))

            if (isMatch) {
                if (!results.any { it.type == LexiconSuggestionType.ARTIST && it.artist.id == artist.id }) {
                    results.add(
                        LexiconSearchSuggestion(
                            type = LexiconSuggestionType.ARTIST,
                            title = cleanArtistName,
                            badge = "🎤 ${artist.genre} (${artist.discography.size} Albums)",
                            artist = artist
                        )
                    )
                }
            }
        }

        // 2. Check Albums & Songs if query is >= 2 chars
        if (query.length >= 2 && results.size < maxResults) {
            for (artist in artistList) {
                if (results.size >= maxResults) break

                if (artist.discography.isNotEmpty()) {
                    for (album in artist.discography) {
                        if (results.size >= maxResults) break

                        val albumTitle = album.title
                        val albumTitleLower = albumTitle.lowercase()

                        // Album Match
                        if (albumTitleLower.contains(query) && !results.any { it.type == LexiconSuggestionType.ALBUM && it.albumTitle == albumTitle && it.artist.id == artist.id }) {
                            results.add(
                                LexiconSearchSuggestion(
                                    type = LexiconSuggestionType.ALBUM,
                                    title = albumTitle,
                                    badge = "💿 Album by ${artist.name} (${album.year.ifEmpty { "N/A" }})",
                                    artist = artist,
                                    albumTitle = albumTitle
                                )
                            )
                        }

                        // Track Matches
                        for (track in album.tracks) {
                            if (results.size >= maxResults) break
                            val trackLower = track.lowercase()

                            if (trackLower.contains(query) && !results.any { it.type == LexiconSuggestionType.TRACK && it.trackName == track && it.artist.id == artist.id }) {
                                results.add(
                                    LexiconSearchSuggestion(
                                        type = LexiconSuggestionType.TRACK,
                                        title = track,
                                        badge = "🎵 Track by ${artist.name}",
                                        artist = artist,
                                        albumTitle = albumTitle,
                                        trackName = track
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        return results
    }

    private fun cleanArtistName(raw: String): String {
        return raw.replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring|vs\\.?|pres\\.?)\\b.*"), "")
            .trim()
            .split(",", ";", "&", "/")[0]
            .replace(Regex("\\s*\\([^)]*\\)"), "")
            .trim()
    }

    private fun normalizeKey(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun normalizeKeyAdvanced(name: String): String {
        var s = name.lowercase().trim()
        if (s.contains("aschere")) {
            s = s.replace("aschere", "usher")
        }
        s = s.replace(Regex("\\b(ii|to|two)\\b"), "2")
            .replace(Regex("\\b(iii)\\b"), "3")
            .replace(Regex("\\b(iv)\\b"), "4")
            .replace(Regex("\\b(and|\\&)\\b"), "and")
            .replace(Regex("boys\\b"), "boyz")

        return s.replace(Regex("[^a-z0-9]"), "").trim()
    }
}
