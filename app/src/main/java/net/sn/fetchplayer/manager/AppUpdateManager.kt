package net.sn.fetchplayer.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import net.sn.fetchplayer.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val htmlUrl: String,
    val isUpdateAvailable: Boolean
)

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/StationNorthMedia/SNfetchPLAYER/releases/latest"

    /**
     * Queries GitHub API for the latest release and checks if a newer version is available.
     */
    suspend fun checkForUpdate(currentVersionName: String): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = openConnectionFollowingRedirects(GITHUB_LATEST_RELEASE_URL)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.w(TAG, "GitHub API returned HTTP response code: ${connection.responseCode}")
                connection.disconnect()
                return@withContext null
            }

            val responseStr = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val json = JSONObject(responseStr)

            val tagName = json.optString("tag_name", "")
            val htmlUrl = json.optString("html_url", "")
            val releaseNotes = json.optString("body", "No release notes provided.")

            var apkUrl = ""
            var apkFileName = ""

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkFileName = name
                        break
                    }
                }
            }

            val cleanCurrent = cleanVersionString(currentVersionName)
            val cleanRemote = cleanVersionString(tagName)

            val isAvailable = isRemoteVersionNewer(cleanCurrent, cleanRemote)

            AppLogger.d(
                TAG,
                "Version Check -> Current: '$cleanCurrent' vs Remote: '$cleanRemote' (Tag: $tagName) -> Available: $isAvailable"
            )

            return@withContext AppUpdateInfo(
                tagName = tagName,
                versionName = cleanRemote,
                releaseNotes = releaseNotes,
                apkDownloadUrl = apkUrl,
                apkFileName = if (apkFileName.isNotEmpty()) apkFileName else "SNfetchPLAYER-update.apk",
                htmlUrl = htmlUrl,
                isUpdateAvailable = isAvailable
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check for app update", e)
            return@withContext null
        }
    }

    /**
     * Helper method to open HttpURLConnection following cross-domain and cross-protocol 301/302/303/307/308 redirects.
     */
    private fun openConnectionFollowingRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        val maxRedirects = 8

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SNfetchPLAYER-App")
                setRequestProperty("Accept", "*/*")
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (!location.isNullOrEmpty()) {
                    currentUrl = location
                    redirects++
                    continue
                }
            }
            return conn
        }
        return (URL(currentUrl).openConnection() as HttpURLConnection)
    }

    /**
     * Cleans version strings like "v1.0.0.4", "1.0.0.4 (2026.09.21-22:12)" down to pure numbers e.g. "1.0.0.4".
     */
    fun cleanVersionString(rawVersion: String): String {
        val trimmed = rawVersion.trim().removePrefix("v").removePrefix("V")
        return trimmed.split(" ")[0].trim()
    }

    /**
     * Compares version string numbers e.g. "1.0.0.4" vs "1.0.0.5".
     */
    fun isRemoteVersionNewer(currentVer: String, remoteVer: String): Boolean {
        if (remoteVer.isEmpty()) return false
        if (currentVer.isEmpty()) return true

        val currentParts = currentVer.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val remoteParts = remoteVer.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(currentParts.size, remoteParts.size)
        for (i in 0 until maxLen) {
            val curr = currentParts.getOrElse(i) { 0 }
            val rem = remoteParts.getOrElse(i) { 0 }
            if (rem > curr) return true
            if (rem < curr) return false
        }
        return false
    }

    /**
     * Downloads the APK file to app external files dir with download progress updates.
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val targetFile = File(targetDir, fileName)

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val connection = openConnectionFollowingRedirects(downloadUrl)

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                AppLogger.e(TAG, "Download failed with HTTP status: ${connection.responseCode} for URL: $downloadUrl")
                connection.disconnect()
                return@withContext null
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    var lastReportPercent = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastReportPercent) {
                                lastReportPercent = percent
                                withContext(Dispatchers.Main) {
                                    onProgress(percent, downloadedBytes, totalBytes)
                                }
                            }
                        }
                    }
                }
            }
            connection.disconnect()

            withContext(Dispatchers.Main) {
                onProgress(100, downloadedBytes, downloadedBytes)
            }

            return@withContext targetFile
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error downloading APK from $downloadUrl", e)
            return@withContext null
        }
    }

    /**
     * Prompts the user to install the downloaded APK via FileProvider Intent.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            if (!apkFile.exists() || apkFile.length() < 100 * 1024) {
                AppLogger.e(TAG, "Cannot install APK: File missing or corrupted (${apkFile.length()} bytes)")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    AppLogger.w(TAG, "REQUEST_INSTALL_PACKAGES permission not granted. Opening settings...")
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                    return false
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to launch APK installation intent", e)
            false
        }
    }
}
