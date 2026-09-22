package net.sn.fetchplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import net.sn.fetchplayer.data.ArtistInfo
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object CacheManager {

    private const val PREFS_NAME = "sn_cache_prefs"
    private const val KEY_CACHE_ENABLED = "cache_enabled"
    private const val WIKI_BIO_FILE = "cached_wiki_bios.json"

    fun isCacheEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CACHE_ENABLED, false) // Default: OFF for TV safety
    }

    fun setCacheEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CACHE_ENABLED, enabled).apply()
    }

    fun getUsedCacheSizeBytes(context: Context): Long {
        var total = 0L
        try {
            val imgDir = File(context.cacheDir, "artist_images")
            if (imgDir.exists()) {
                imgDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
            }
            val bioFile = File(context.filesDir, WIKI_BIO_FILE)
            if (bioFile.exists()) {
                total += bioFile.length()
            }
            val mediaDir = File(context.cacheDir, "media_cache")
            if (mediaDir.exists()) {
                mediaDir.walkTopDown().forEach { if (it.isFile) total += it.length() }
            }
        } catch (_: Exception) {}
        return total
    }

    fun getFormattedCacheSize(context: Context): String {
        val bytes = getUsedCacheSizeBytes(context)
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes Bytes"
        }
    }

    fun clearAllCache(context: Context): Boolean {
        return try {
            val imgDir = File(context.cacheDir, "artist_images")
            if (imgDir.exists()) imgDir.deleteRecursively()

            val bioFile = File(context.filesDir, WIKI_BIO_FILE)
            if (bioFile.exists()) bioFile.delete()

            val mediaDir = File(context.cacheDir, "media_cache")
            if (mediaDir.exists()) mediaDir.deleteRecursively()

            true
        } catch (e: Exception) {
            AppLogger.e("CacheManager", "Failed to clear cache", e)
            false
        }
    }

    fun getCachedWikiBio(context: Context, artistName: String): ArtistInfo? {
        if (!isCacheEnabled(context)) return null
        return try {
            val bioFile = File(context.filesDir, WIKI_BIO_FILE)
            if (!bioFile.exists()) return null

            val jsonStr = bioFile.readText()
            val root = JSONObject(jsonStr)
            val key = artistName.lowercase().trim()
            if (!root.has(key)) return null

            val obj = root.getJSONObject(key)
            val title = obj.optString("title", artistName)
            val extract = obj.optString("extract", "")
            val imageUrl = if (obj.has("imageUrl") && !obj.isNull("imageUrl")) obj.optString("imageUrl") else null

            var bitmap: Bitmap? = null
            if (!imageUrl.isNullOrEmpty()) {
                bitmap = getCachedImageBitmap(context, imageUrl)
            }

            ArtistInfo(title = title, imageUrl = imageUrl, extract = extract, imageBitmap = bitmap)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCachedWikiBio(context: Context, artistName: String, info: ArtistInfo) {
        if (!isCacheEnabled(context)) return
        try {
            val bioFile = File(context.filesDir, WIKI_BIO_FILE)
            val root = if (bioFile.exists()) {
                try { JSONObject(bioFile.readText()) } catch (_: Exception) { JSONObject() }
            } else {
                JSONObject()
            }

            val key = artistName.lowercase().trim()
            val obj = JSONObject().apply {
                put("title", info.title)
                put("extract", info.extract)
                put("imageUrl", info.imageUrl ?: "")
            }
            root.put(key, obj)

            bioFile.writeText(root.toString())

            if (info.imageBitmap != null && !info.imageUrl.isNullOrEmpty()) {
                saveCachedImageBitmap(context, info.imageUrl, info.imageBitmap!!)
            }
        } catch (e: Exception) {
            AppLogger.e("CacheManager", "Failed to save wiki bio cache", e)
        }
    }

    fun getCachedImageBitmap(context: Context, url: String): Bitmap? {
        if (!isCacheEnabled(context)) return null
        return try {
            val hash = url.hashCode().toString()
            val imgFile = File(File(context.cacheDir, "artist_images"), "$hash.jpg")
            if (imgFile.exists()) {
                BitmapFactory.decodeFile(imgFile.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun saveCachedImageBitmap(context: Context, url: String, bitmap: Bitmap) {
        if (!isCacheEnabled(context)) return
        try {
            val imgDir = File(context.cacheDir, "artist_images")
            if (!imgDir.exists()) imgDir.mkdirs()

            val hash = url.hashCode().toString()
            val imgFile = File(imgDir, "$hash.jpg")
            FileOutputStream(imgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        } catch (e: Exception) {
            AppLogger.e("CacheManager", "Failed to save image cache", e)
        }
    }
}
