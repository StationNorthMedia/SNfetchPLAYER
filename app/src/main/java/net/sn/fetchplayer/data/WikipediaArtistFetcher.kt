package net.sn.fetchplayer.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.sn.fetchplayer.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class ArtistInfo(
    val title: String,
    val imageUrl: String?,
    val extract: String,
    var imageBitmap: Bitmap? = null
)

object WikipediaArtistFetcher {

    private const val TAG = "WikipediaArtistFetcher"
    private val cache = ConcurrentHashMap<String, ArtistInfo>()

    suspend fun fetchArtistInfo(artistName: String): ArtistInfo? = withContext(Dispatchers.IO) {
        val cleanName = cleanArtistName(artistName)
        if (cleanName.isEmpty() || isStationContentName(cleanName)) return@withContext null

        cache[cleanName]?.let { return@withContext it }

        try {
            // 1. Check local SN-Lexikon repository first!
            val lexiconArtist = ArtistLexiconRepository.findArtist(cleanName)
            if (lexiconArtist != null && lexiconArtist.bio.isNotEmpty()) {
                AppLogger.d(TAG, "Found local SN-Lexikon bio for artist: ${lexiconArtist.name}")
                
                // Fetch image asynchronously from Wikipedia summary API
                var imgUrl: String? = null
                val directSummary = fetchDirectSummary(lexiconArtist.name)
                if (directSummary != null && !directSummary.imageUrl.isNullOrEmpty()) {
                    imgUrl = directSummary.imageUrl
                } else {
                    val searchTitle = searchWikipediaTitle(lexiconArtist.name)
                    if (searchTitle != null) {
                        val searchSummary = fetchDirectSummary(searchTitle)
                        imgUrl = searchSummary?.imageUrl
                    }
                }

                val info = ArtistInfo(
                    title = lexiconArtist.name,
                    imageUrl = imgUrl,
                    extract = lexiconArtist.bio
                )

                if (!imgUrl.isNullOrEmpty()) {
                    info.imageBitmap = downloadBitmap(imgUrl)
                }

                cache[cleanName] = info
                return@withContext info
            }

            // 2. Network Fallback if artist not in local lexicon
            AppLogger.d(TAG, "Fetching Wikipedia summary for artist: $cleanName")
            var info = fetchDirectSummary(cleanName)

            if (info == null || info.extract.isEmpty()) {
                val searchTitle = searchWikipediaTitle(cleanName)
                if (searchTitle != null && searchTitle != cleanName) {
                    info = fetchDirectSummary(searchTitle)
                }
            }

            if (info != null) {
                val imgUrl = info.imageUrl
                if (!imgUrl.isNullOrEmpty()) {
                    info.imageBitmap = downloadBitmap(imgUrl)
                }
                cache[cleanName] = info
                return@withContext info
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching Wikipedia info for $cleanName", e)
        }

        return@withContext null
    }

    private fun isStationContentName(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("station north") ||
                lower.contains("station id") ||
                lower.contains("sn-tv") ||
                lower.contains("sn-radio") ||
                lower.contains("jingle")
    }

    private fun cleanArtistName(raw: String): String {
        return raw.replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring|vs\\.?|pres\\.?)\\b.*"), "")
            .trim()
            .split(",", ";", "&", "/")[0]
            .trim()
    }

    private fun fetchDirectSummary(title: String): ArtistInfo? {
        val encodedTitle = URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
        val urlString = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
        
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4000
            readTimeout = 4000
            setRequestProperty("User-Agent", "SNfetchPLAYER/1.0 (Android; Contact: dev@stationnorth.tv)")
        }

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)

            val pageTitle = json.optString("title", title)
            val extract = json.optString("extract", "")
            val thumbnailObj = json.optJSONObject("thumbnail")
            val imageUrl = thumbnailObj?.optString("source")

            if (extract.isNotEmpty()) {
                return ArtistInfo(title = pageTitle, imageUrl = imageUrl, extract = extract)
            }
        }
        return null
    }

    private fun searchWikipediaTitle(query: String): String? {
        val searchKeywords = arrayOf(
            "$query (singer OR band OR group OR musician)",
            "$query singer",
            "$query band",
            "$query musician"
        )

        for (kw in searchKeywords) {
            val encodedQuery = URLEncoder.encode(kw, "UTF-8")
            val urlString = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encodedQuery&format=json"

            val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "SNfetchPLAYER/1.0 (Android; Contact: dev@stationnorth.tv)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val searchResults = json.optJSONObject("query")?.optJSONArray("search")
                if (searchResults != null && searchResults.length() > 0) {
                    val title = searchResults.getJSONObject(0).optString("title")
                    if (title.isNotEmpty()) return title
                }
            }
        }
        return null
    }

    private fun downloadBitmap(imageUrl: String): Bitmap? {
        return try {
            val conn = URL(imageUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doInput = true
            conn.connect()
            BitmapFactory.decodeStream(conn.inputStream)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to download image: $imageUrl", e)
            null
        }
    }
}
