package net.sn.fetchplayer.manager

import android.content.Context
import net.sn.fetchplayer.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class SyncProgress(
    val totalCount: Int = 0,
    val downloadedCount: Int = 0,
    val progressPercent: Int = 0,
    val statusMessage: String = "Idle",
    val isSyncing: Boolean = false
)

object RemoteAssetSyncManager {

    private const val TAG = "RemoteAssetSyncManager"
    private const val PRIMARY_MANIFEST_URL = "https://raw.githubusercontent.com/StationNorthMedia/SNfetchPLAYER/main/remote_assets/catalog_manifest.json"
    private const val FALLBACK_MANIFEST_URL = "https://gitlab.com/station-north-net-group/snfetchplayer/-/raw/main/remote_assets/catalog_manifest.json"

    private val _syncState = MutableStateFlow(SyncProgress())
    val syncState: StateFlow<SyncProgress> = _syncState

    private var syncJob: Job? = null

    fun checkLocalAssetsStatus(context: Context): SyncProgress {
        val jinglesDir = File(context.filesDir, "remote_assets/jingles").apply { mkdirs() }
        val tvIdsDir = File(context.filesDir, "remote_assets/tv_ids").apply { mkdirs() }
        val comicDir = File(context.filesDir, "remote_assets/comic").apply { mkdirs() }
        val comicAudioDir = File(comicDir, "audio").apply { mkdirs() }

        val localJingles = jinglesDir.listFiles()?.count { it.length() > 5 * 1024 } ?: 0
        val localTvIds = tvIdsDir.listFiles()?.count { it.length() > 5 * 1024 } ?: 0
        val localComicImages = comicDir.listFiles()?.count { it.isFile && it.length() > 1 * 1024 } ?: 0
        val localComicAudio = comicAudioDir.listFiles()?.count { it.isFile && it.length() > 5 * 1024 } ?: 0
        val localComicTotal = localComicImages + localComicAudio

        val currentCount = localJingles + localTvIds + localComicTotal
        val knownTotal = 335 // 163 Jingles + 42 TV IDs + 130 Comic Assets

        val percent = if (knownTotal > 0) ((currentCount.toFloat() / knownTotal) * 100).toInt().coerceIn(0, 100) else 0
        val statusMsg = "Local Storage: $localJingles/163 Jingles • $localTvIds/42 TV IDs • $localComicTotal/130 Chronicles ($percent% ready)"

        val state = SyncProgress(
            totalCount = knownTotal,
            downloadedCount = currentCount,
            progressPercent = percent,
            statusMessage = statusMsg,
            isSyncing = syncJob?.isActive == true
        )
        _syncState.value = state
        return state
    }

    fun startRemoteAssetSync(context: Context, onProgressUpdate: ((SyncProgress) -> Unit)? = null) {
        if (syncJob?.isActive == true) {
            AppLogger.d(TAG, "Sync already in progress.")
            return
        }

        syncJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                updateProgress(SyncProgress(statusMessage = "Fetching remote catalog_manifest.json...", isSyncing = true), onProgressUpdate)

                var manifestJsonStr = downloadString(PRIMARY_MANIFEST_URL)
                if (manifestJsonStr.isNullOrEmpty()) {
                    AppLogger.w(TAG, "Primary GitHub manifest failed. Retrying with GitLab Fallback...")
                    manifestJsonStr = downloadString(FALLBACK_MANIFEST_URL)
                }

                if (manifestJsonStr.isNullOrEmpty()) {
                    updateProgress(SyncProgress(statusMessage = "Failed to load catalog_manifest.json from GitHub & GitLab mirrors.", isSyncing = false), onProgressUpdate)
                    return@launch
                }

                val json = JSONObject(manifestJsonStr)
                val jinglesArray = json.optJSONArray("jingles")
                val tvIdsArray = json.optJSONArray("tv_ids")
                val comicArray = json.optJSONArray("comic")

                val downloadList = mutableListOf<Pair<String, File>>() // Primary URL to Local Target File

                val jinglesDir = File(context.filesDir, "remote_assets/jingles").apply { mkdirs() }
                val tvIdsDir = File(context.filesDir, "remote_assets/tv_ids").apply { mkdirs() }
                val comicDir = File(context.filesDir, "remote_assets/comic").apply { mkdirs() }

                if (jinglesArray != null) {
                    for (i in 0 until jinglesArray.length()) {
                        val item = jinglesArray.getJSONObject(i)
                        val fileUrl = item.optString("url")
                        val fileName = fileUrl.substringAfterLast("/")
                        if (fileName.isNotEmpty()) {
                            val targetFile = File(jinglesDir, fileName)
                            if (!targetFile.exists() || targetFile.length() < 5 * 1024) {
                                downloadList.add(Pair(fileUrl, targetFile))
                            }
                        }
                    }
                }

                if (tvIdsArray != null) {
                    for (i in 0 until tvIdsArray.length()) {
                        val item = tvIdsArray.getJSONObject(i)
                        val fileUrl = item.optString("url")
                        val fileName = fileUrl.substringAfterLast("/")
                        if (fileName.isNotEmpty()) {
                            val targetFile = File(tvIdsDir, fileName)
                            if (!targetFile.exists() || targetFile.length() < 5 * 1024) {
                                downloadList.add(Pair(fileUrl, targetFile))
                            }
                        }
                    }
                }

                if (comicArray != null) {
                    for (i in 0 until comicArray.length()) {
                        val item = comicArray.getJSONObject(i)
                        val fileUrl = item.optString("url")
                        val relPath = item.optString("id") // e.g. "Kapitel_01_1.webp" or "audio/p_001.mp3"
                        if (relPath.isNotEmpty()) {
                            val targetFile = File(comicDir, relPath).apply { parentFile?.mkdirs() }
                            val minSize = if (relPath.endsWith(".json")) 100L else 1 * 1024L
                            if (!targetFile.exists() || targetFile.length() < minSize) {
                                downloadList.add(Pair(fileUrl, targetFile))
                            }
                        }
                    }
                }

                val totalCount = (jinglesArray?.length() ?: 0) + (tvIdsArray?.length() ?: 0) + (comicArray?.length() ?: 0)
                val alreadyDownloaded = totalCount - downloadList.size

                if (downloadList.isEmpty()) {
                    updateProgress(
                        SyncProgress(
                            totalCount = totalCount,
                            downloadedCount = totalCount,
                            progressPercent = 100,
                            statusMessage = "All $totalCount Remote Assets (Jingles, TV IDs & Chronicles) are downloaded & 100% up to date!",
                            isSyncing = false
                        ),
                        onProgressUpdate
                    )
                    return@launch
                }

                for ((index, pair) in downloadList.withIndex()) {
                    val (fileUrl, targetFile) = pair
                    val currentDownloaded = alreadyDownloaded + index + 1
                    val percent = ((currentDownloaded.toFloat() / totalCount) * 100).toInt().coerceIn(0, 100)
                    val fileName = targetFile.name

                    updateProgress(
                        SyncProgress(
                            totalCount = totalCount,
                            downloadedCount = currentDownloaded,
                            progressPercent = percent,
                            statusMessage = "Downloading [$currentDownloaded / $totalCount]: $fileName ($percent%)",
                            isSyncing = true
                        ),
                        onProgressUpdate
                    )

                    downloadFileWithFallback(fileUrl, targetFile)
                }

                updateProgress(
                    SyncProgress(
                        totalCount = totalCount,
                        downloadedCount = totalCount,
                        progressPercent = 100,
                        statusMessage = "Sync Complete! All $totalCount Remote Assets downloaded and ready.",
                        isSyncing = false
                    ),
                    onProgressUpdate
                )
                AppLogger.d(TAG, "Completed downloading all remote assets.")

            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during asset sync", e)
                updateProgress(SyncProgress(statusMessage = "Sync Error: ${e.message}", isSyncing = false), onProgressUpdate)
            }
        }
    }

    private suspend fun updateProgress(progress: SyncProgress, onProgressUpdate: ((SyncProgress) -> Unit)?) = withContext(Dispatchers.Main) {
        _syncState.value = progress
        onProgressUpdate?.invoke(progress)
    }

    private fun downloadString(urlString: String): String? {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadFileWithFallback(primaryUrl: String, outputFile: File) {
        val success = downloadFile(primaryUrl, outputFile)
        if (!success) {
            val gitlabFallbackUrl = primaryUrl.replace(
                "https://raw.githubusercontent.com/StationNorthMedia/SNfetchPLAYER/main/",
                "https://gitlab.com/station-north-net-group/snfetchplayer/-/raw/main/"
            )
            AppLogger.w(TAG, "Primary URL failed: $primaryUrl. Trying GitLab Fallback: $gitlabFallbackUrl")
            downloadFile(gitlabFallbackUrl, outputFile)
        }
    }

    private fun downloadFile(urlString: String, outputFile: File): Boolean {
        return try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                outputFile.parentFile?.mkdirs()
                val tempFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val minSize = if (outputFile.name.endsWith(".json")) 10L else 500L
                if (tempFile.length() > minSize) {
                    tempFile.renameTo(outputFile)
                    true
                } else {
                    tempFile.delete()
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed downloading file $urlString", e)
            false
        }
    }
}
