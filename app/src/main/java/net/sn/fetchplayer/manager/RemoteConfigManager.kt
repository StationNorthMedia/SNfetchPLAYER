package net.sn.fetchplayer.manager

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sn.fetchplayer.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"
    private const val REMOTE_CONFIG_URL =
        "https://raw.githubusercontent.com/StationNorthMedia/SNfetchPLAYER/main/remote_assets/extractor_rules.json"
    private const val INVIDIOUS_REGISTRY_URL =
        "https://api.invidious.io/instances.json?sort_by=type,health"
    private const val LOCAL_CACHE_FILE = "extractor_rules_cache.json"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private var pipedInstances: List<String> = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.drgns.space",
        "https://pipedapi.mha.fi"
    )

    private var invidiousInstances: List<String> = listOf(
        "https://invidious.f5.si",
        "https://vid.puffyan.us",
        "https://invidious.flokinet.to",
        "https://invidious.nerdvpn.de",
        "https://inv.tux.pizza"
    )

    private var cobaltInstances: List<String> = listOf(
        "https://api.cobalt.tools"
    )

    var androidClientVersion: String = "21.02.35"
        private set

    var androidUserAgent: String = "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip"
        private set

    fun getPipedInstances(): List<String> = pipedInstances
    fun getInvidiousInstances(): List<String> = invidiousInstances
    fun getCobaltInstances(): List<String> = cobaltInstances

    suspend fun fetchAndCacheConfig(context: Context) = withContext(Dispatchers.IO) {
        loadFromCache(context)

        // 1. Fetch OTA Github extractor rules
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

        // 2. Fetch live healthy instances from official Invidious Registry API
        try {
            fetchLiveInvidiousRegistry()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error fetching Invidious live registry", e)
        }
    }

    private fun fetchLiveInvidiousRegistry() {
        AppLogger.d(TAG, "Fetching live healthy instances from api.invidious.io...")
        val request = Request.Builder()
            .url(INVIDIOUS_REGISTRY_URL)
            .header("User-Agent", "SNfetchPLAYER-App")
            .build()

        val response = httpClient.newCall(request).execute()
        if (response.isSuccessful) {
            val jsonStr = response.body?.string()
            if (!jsonStr.isNullOrEmpty()) {
                val jsonArr = JSONArray(jsonStr)
                val liveList = mutableListOf<String>()
                for (i in 0 until jsonArr.length()) {
                    val entry = jsonArr.getJSONArray(i)
                    if (entry.length() >= 2) {
                        val domain = entry.getString(0)
                        val info = entry.getJSONObject(1)
                        val type = info.optString("type", "")
                        val health = info.optDouble("health", 0.0)

                        if (type.equals("https", ignoreCase = true) && health > 50.0) {
                            liveList.add("https://$domain")
                        }
                    }
                }
                if (liveList.isNotEmpty()) {
                    val combined = (liveList + invidiousInstances).distinct()
                    invidiousInstances = combined
                    AppLogger.d(TAG, "Successfully merged ${liveList.size} live Invidious instances from registry (Total: ${invidiousInstances.size})")
                }
            }
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

            val clientVer = json.optString("android_client_version", "")
            if (clientVer.isNotEmpty()) {
                androidClientVersion = clientVer
                androidUserAgent = "com.google.android.youtube/$clientVer (Linux; U; Android 11) gzip"
                AppLogger.d(TAG, "Updated InnerTube Android Client Version via OTA: $clientVer")
            }

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

            val cobaltArr = json.optJSONArray("cobalt_instances")
            if (cobaltArr != null && cobaltArr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until cobaltArr.length()) {
                    val url = cobaltArr.getString(i).trim().removeSuffix("/")
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        list.add(url)
                    }
                }
                if (list.isNotEmpty()) {
                    cobaltInstances = list
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error parsing remote extractor rules JSON", e)
        }
    }
}
