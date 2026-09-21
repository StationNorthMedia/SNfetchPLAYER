package com.example.snfetchplayer.manager

import android.content.Context
import android.net.Uri
import com.example.snfetchplayer.R
import com.example.snfetchplayer.model.PlaybackMode
import com.example.snfetchplayer.model.Track
import com.example.snfetchplayer.util.AppLogger
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class StationContentManager(private val context: Context) {

    companion object {
        private const val MIN_MEDIA_FILE_SIZE_BYTES = 10 * 1024L // 10 KB threshold
    }

    private data class StationIdSource(val idName: String, val uriString: String)

    private val tvIdCounter = AtomicInteger(0)
    private val radioJingleCounter = AtomicInteger(0)

    fun getStationContentForMode(mode: PlaybackMode): Track? {
        val packageName = context.packageName

        return if (mode == PlaybackMode.SN_TV) {
            val tvSources = getTvStationIdSources()
            if (tvSources.isNotEmpty()) {
                val index = tvIdCounter.getAndIncrement() % tvSources.size
                val source = tvSources[index]
                AppLogger.d("StationContentManager", "Selected Station ID: ${source.idName} ($index / ${tvSources.size})")

                Track(
                    id = "sn_id_${source.idName}",
                    title = "Station North ID (${source.idName})",
                    artist = "Station North TV",
                    resolvedStreamUrl = source.uriString,
                    isStationContent = true,
                    isLocalVideo = true,
                    localResourceUri = source.uriString
                )
            } else {
                val resId = R.raw.sn_id_1
                if (!isValidRawMediaResource(resId)) {
                    AppLogger.d("StationContentManager", "sn_id_1.mp4 is missing or placeholder (<10KB). Skipping Station ID.")
                    return null
                }
                val resUri = "android.resource://$packageName/$resId"
                val count = tvIdCounter.getAndIncrement() + 1
                Track(
                    id = "sn_id_$count",
                    title = "Station North ID #$count",
                    artist = "Station North TV",
                    resolvedStreamUrl = resUri,
                    isStationContent = true,
                    isLocalVideo = true,
                    localResourceUri = resUri
                )
            }
        } else {
            val resId = R.raw.sn_jingle_1
            if (!isValidRawMediaResource(resId)) {
                AppLogger.d("StationContentManager", "sn_jingle_1.mp3 is missing or placeholder (<10KB). Skipping Station Jingle.")
                return null
            }
            val resUri = "android.resource://$packageName/$resId"
            val count = radioJingleCounter.getAndIncrement() + 1
            Track(
                id = "sn_jingle_$count",
                title = "Station North Jingle #$count",
                artist = "Station North Radio",
                resolvedStreamUrl = resUri,
                isStationContent = true,
                isLocalVideo = false,
                localResourceUri = resUri
            )
        }
    }

    private fun getTvStationIdSources(): List<StationIdSource> {
        val sources = mutableListOf<StationIdSource>()

        // 1. Check downloaded files on internal/external storage
        val candidateDirs = listOf(
            File(context.filesDir, "SNid"),
            File(context.getExternalFilesDir(null), "SNid")
        )

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    val lower = name.lowercase()
                    (lower.startsWith("sjsn") || lower.startsWith("sn_id")) && (lower.endsWith(".mp4") || lower.endsWith(".mkv"))
                }?.sortedBy { it.name } ?: emptyList()

                val validFiles = files.filter { it.length() >= MIN_MEDIA_FILE_SIZE_BYTES }
                for (file in validFiles) {
                    sources.add(StationIdSource(file.nameWithoutExtension, Uri.fromFile(file).toString()))
                }
            }
        }

        // 2. Check bundled asset files in assets/SNid/
        try {
            val assetFiles = context.assets.list("SNid")?.filter { name ->
                val lower = name.lowercase()
                (lower.startsWith("sjsn") || lower.startsWith("sn_id")) && (lower.endsWith(".mp4") || lower.endsWith(".mkv"))
            }?.sorted() ?: emptyList()

            for (assetName in assetFiles) {
                val nameWithoutExt = assetName.substringBeforeLast(".")
                // Avoid duplicates if downloaded file with same name exists
                if (sources.none { it.idName.equals(nameWithoutExt, ignoreCase = true) }) {
                    sources.add(StationIdSource(nameWithoutExt, "asset:///SNid/$assetName"))
                }
            }
        } catch (e: Exception) {
            AppLogger.d("StationContentManager", "No assets/SNid/ directory found or error reading assets: ${e.message}")
        }

        return sources
    }

    private fun isValidRawMediaResource(resId: Int): Boolean {
        return try {
            val afd = context.resources.openRawResourceFd(resId)
            val length = afd?.length ?: 0L
            afd?.close()
            length >= MIN_MEDIA_FILE_SIZE_BYTES
        } catch (e: Exception) {
            false
        }
    }
}
