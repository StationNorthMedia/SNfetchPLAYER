package net.sn.fetchplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.sn.fetchplayer.util.AppLogger
import net.sn.fetchplayer.util.CacheManager
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

    suspend fun fetchArtistInfo(artistName: String, context: Context? = null): ArtistInfo? = withContext(Dispatchers.IO) {
        val cleanName = cleanArtistName(artistName)
        if (cleanName.isEmpty() || isStationContentName(cleanName)) return@withContext null

        // 0. Check in-memory RAM cache
        cache[cleanName]?.let { return@withContext it }

        // 0b. Check persistent Disk Cache if enabled
        if (context != null && CacheManager.isCacheEnabled(context)) {
            val diskCached = CacheManager.getCachedWikiBio(context, cleanName)
            if (diskCached != null && diskCached.extract.isNotEmpty()) {
                AppLogger.d(TAG, "Found disk-cached Wikipedia bio for artist: $cleanName")
                cache[cleanName] = diskCached
                return@withContext diskCached
            }
        }

        try {
            // 1. Check local SN-Lexikon repository first!
            val lexiconArtist = ArtistLexiconRepository.findArtist(cleanName)
            if (lexiconArtist != null && lexiconArtist.bio.isNotEmpty()) {
                AppLogger.d(TAG, "Found local SN-Lexikon bio for artist: ${lexiconArtist.name}")
                
                val cleanBio = cleanExtractText(lexiconArtist.bio)
                var imgUrl: String? = fetchImageUrlForArtist(lexiconArtist.name)
                if (imgUrl.isNullOrEmpty()) {
                    imgUrl = fetchImageUrlForArtist(cleanName)
                }

                var bitmap: Bitmap? = null
                if (context != null && !imgUrl.isNullOrEmpty()) {
                    bitmap = CacheManager.getCachedImageBitmap(context, imgUrl)
                }
                if (bitmap == null && !imgUrl.isNullOrEmpty()) {
                    bitmap = downloadBitmap(imgUrl)
                }

                val info = ArtistInfo(
                    title = lexiconArtist.name,
                    imageUrl = imgUrl,
                    extract = cleanBio,
                    imageBitmap = bitmap
                )

                cache[cleanName] = info
                if (context != null && CacheManager.isCacheEnabled(context)) {
                    CacheManager.saveCachedWikiBio(context, cleanName, info)
                }
                return@withContext info
            }

            // 2. English Wikipedia Summary Fallback
            AppLogger.d(TAG, "Fetching English Wikipedia summary for artist: $cleanName")
            val info: ArtistInfo? = fetchSummaryWithFallbacks(cleanName)

            if (info != null) {
                val imgUrl = info.imageUrl
                var bitmap: Bitmap? = null
                if (context != null && !imgUrl.isNullOrEmpty()) {
                    bitmap = CacheManager.getCachedImageBitmap(context, imgUrl)
                }
                if (bitmap == null && !imgUrl.isNullOrEmpty()) {
                    bitmap = downloadBitmap(imgUrl)
                }
                info.imageBitmap = bitmap

                cache[cleanName] = info
                if (context != null && CacheManager.isCacheEnabled(context)) {
                    CacheManager.saveCachedWikiBio(context, cleanName, info)
                }
                return@withContext info
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching English Wikipedia info for $cleanName", e)
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
            .replace(Regex("\\s*\\([^)]*\\)"), "")
            .trim()
    }

    private fun cleanExtractText(raw: String): String {
        return raw.replace(Regex("<[^>]*>"), "")
            .replace(Regex("\\r?\\n"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun isDisambiguation(type: String?, extract: String): Boolean {
        if (type.equals("disambiguation", ignoreCase = true)) return true
        val lower = extract.lowercase()
        return lower.contains("may refer to:") ||
                lower.contains("refers to:") ||
                lower.contains("is a disambiguation")
    }

    private fun isNonMusicianPage(title: String, extract: String): Boolean {
        val lowerTitle = title.lowercase()
        val lowerExtract = extract.lowercase()

        return lowerTitle.contains("(film)") ||
                lowerTitle.contains("(movie)") ||
                lowerTitle.contains("film)") ||
                lowerExtract.contains("action comedy film") ||
                lowerExtract.contains("directed by michael bay") ||
                lowerExtract.contains("american action film") ||
                lowerExtract.contains("is a feature film") ||
                lowerExtract.contains("film directed by") ||
                lowerExtract.contains("video game")
    }

    private fun fetchSummaryWithFallbacks(cleanName: String): ArtistInfo? {
        val candidateTitles = listOf(
            "$cleanName (musician)",
            "$cleanName (singer)",
            "$cleanName (band)",
            "$cleanName (rapper)",
            "$cleanName (group)",
            cleanName
        )

        // Try English Wikipedia REST API endpoint across candidate titles
        for (cand in candidateTitles) {
            val info = fetchDirectSummary(cand)
            if (info != null && info.extract.isNotEmpty()) {
                return info
            }
        }

        // Try searching English Wikipedia search API
        val searchKw = "$cleanName musician"
        val searchTitle = searchWikipediaTitle(searchKw)
        if (searchTitle != null) {
            val info = fetchDirectSummary(searchTitle)
            if (info != null && info.extract.isNotEmpty()) {
                return info
            }
        }

        return null
    }

    private fun fetchImageUrlForArtist(artistName: String): String? {
        val candidates = listOf(
            "$artistName (musician)",
            "$artistName (singer)",
            "$artistName (band)",
            artistName
        )

        for (cand in candidates) {
            val summary = fetchDirectSummary(cand)
            if (summary != null && !summary.imageUrl.isNullOrEmpty()) {
                return summary.imageUrl
            }
        }
        return null
    }

    private fun fetchDirectSummary(title: String): ArtistInfo? {
        return try {
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

                val pageType = json.optString("type", "")
                val pageTitle = json.optString("title", title)
                val rawExtract = json.optString("extract", "")
                val cleanedExtract = cleanExtractText(rawExtract)

                if (isDisambiguation(pageType, cleanedExtract) || isNonMusicianPage(pageTitle, cleanedExtract)) {
                    AppLogger.d(TAG, "Skipping disambiguation or non-musician page for $title on en.wikipedia")
                    return null
                }

                val thumbnailObj = json.optJSONObject("thumbnail")
                val imageUrl = thumbnailObj?.optString("source")

                if (cleanedExtract.isNotEmpty()) {
                    return ArtistInfo(title = pageTitle, imageUrl = imageUrl, extract = cleanedExtract)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun searchWikipediaTitle(query: String): String? {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
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
            null
        } catch (e: Exception) {
            null
        }
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
