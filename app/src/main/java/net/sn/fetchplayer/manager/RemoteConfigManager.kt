package net.sn.fetchplayer.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sn.fetchplayer.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"
    private const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/StationNorthMedia/SNfetchPLAYER/main/remote_assets/extractor_rules.json"
    private const val LOCAL_CACHE_FILE = "extractor_rules_cache.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var pipedInstances: List<String> = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.drgns.space",
        "https://pipedapi.mha.fi"
    )

    private var invidiousInstances: List<String> = listOf(
        "https://vid.puffyan.us",
        "https://invidious.flokinet.to",
        "https://invidious.nerdvpn.de",
        "https://inv.tux.pizza"
    )

    fun getPipedInstances(): List<String> = pipedInstances
    fun getInvidiousInstances(): List<String> = invidiousInstances

    suspend fun fetchAndCacheConfig(context: Context) = withContext(Dispatchers.IO) {
        loadFromCache(context)

        try {
            val request = Request.Builder()
                .url(REMOTE_CONFIG_URL)
                .header("User-Agent", "SNfetchPLAYER-App")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string()
                if (!jsonStr.isNullOrEmpty()) {
                    parseAndSetJson(jsonStr)
                    saveToCache(context, jsonStr)
                    AppLogger.d(TAG, "Successfully updated OTA extractor rules from GitHub")
                }
            } else {
                AppLogger.w(TAG, "Failed to download remote extractor config: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching remote extractor config", e)
        }
    }

    private fun loadFromCache(context: Context) {
        try {
            val cacheFile = File(context.filesDir, LOCAL_CACHE_FILE)
            if (cacheFile.exists()) {
                val jsonStr = cacheFile.readText()
                parseAndSetJson(jsonStr)
                AppLogger.d(TAG, "Loaded OTA extractor rules from local cache")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load local extractor rules cache", e)
        }
    }

    private fun saveToCache(context: Context, jsonStr: String) {
        try {
            val cacheFile = File(context.filesDir, LOCAL_CACHE_FILE)
            cacheFile.writeText(jsonStr)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save extractor rules cache", e)
        }
    }

    private fun parseAndSetJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)

            val pipedArr = json.optJSONArray("piped_instances")
            if (pipedArr != null && pipedArr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until pipedArr.length()) {
                    val url = pipedArr.getString(i).trim().removeSuffix("/")
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        list.add(url)
                    }
                }
                if (list.isNotEmpty()) {
                    pipedInstances = list
                }
            }

            val invidiousArr = json.optJSONArray("invidious_instances")
            if (invidiousArr != null && invidiousArr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until invidiousArr.length()) {
                    val url = invidiousArr.getString(i).trim().removeSuffix("/")
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        list.add(url)
                    }
                }
                if (list.isNotEmpty()) {
                    invidiousInstances = list
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing remote extractor rules JSON", e)
        }
    }
}
