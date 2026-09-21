package net.sn.fetchplayer.manager

import android.content.Context
import android.net.Uri
import net.sn.fetchplayer.R
import net.sn.fetchplayer.model.PlaybackMode
import net.sn.fetchplayer.model.Track
import net.sn.fetchplayer.util.AppLogger
import java.io.File

class StationContentManager(private val context: Context) {

    companion object {
        private const val MIN_MEDIA_FILE_SIZE_BYTES = 10 * 1024L // 10 KB threshold
    }

    private data class StationIdSource(val idName: String, val uriString: String)
    private data class RadioJingleSource(val jingleName: String, val uriString: String)

    private val tvIdShufflePool = mutableListOf<StationIdSource>()
    private val radioJingleShufflePool = mutableListOf<RadioJingleSource>()

    @Synchronized
    fun getStationContentForMode(mode: PlaybackMode): Track? {
        return if (mode == PlaybackMode.SN_TV) {
            if (tvIdShufflePool.isEmpty()) {
                val allTvSources = getTvStationIdSources()
                if (allTvSources.isEmpty()) {
                    AppLogger.d("StationContentManager", "No Station TV IDs found.")
                    return null
                }
                tvIdShufflePool.addAll(allTvSources)
                tvIdShufflePool.shuffle()
                AppLogger.d("StationContentManager", "Refreshed & Shuffled TV ID pool (${tvIdShufflePool.size} items)")
            }

            val source = tvIdShufflePool.removeAt(0)
            AppLogger.d("StationContentManager", "Selected Shuffled TV ID: ${source.idName} (${tvIdShufflePool.size} remaining in pool)")

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
            if (radioJingleShufflePool.isEmpty()) {
                val allJingles = getEmbeddedRadioJingles() + getExternalRadioJingles()
                if (allJingles.isEmpty()) {
                    AppLogger.d("StationContentManager", "No Radio Jingles found.")
                    return null
                }
                radioJingleShufflePool.addAll(allJingles)
                radioJingleShufflePool.shuffle()
                AppLogger.d("StationContentManager", "Refreshed & Shuffled Radio Jingle pool (${radioJingleShufflePool.size} items)")
            }

            val jingle = radioJingleShufflePool.removeAt(0)
            AppLogger.d("StationContentManager", "Selected Shuffled Radio Jingle: ${jingle.jingleName} (${radioJingleShufflePool.size} remaining in pool)")

            Track(
                id = "sn_jingle_${jingle.jingleName}",
                title = "Station North Jingle (${jingle.jingleName})",
                artist = "Station North Radio",
                resolvedStreamUrl = jingle.uriString,
                isStationContent = true,
                isLocalVideo = false,
                localResourceUri = jingle.uriString
            )
        }
    }

    private fun getEmbeddedRadioJingles(): List<RadioJingleSource> {
        val list = mutableListOf<RadioJingleSource>()
        val packageName = context.packageName

        // 1. Check embedded Manjaro Lounge Queen Talk 0001 - 0069
        for (i in 1..69) {
            val name = String.format("sn_manjaro_lounge_queen_talk_%04d", i)
            val resId = context.resources.getIdentifier(name, "raw", packageName)
            if (resId != 0 && isValidRawMediaResource(resId)) {
                val uriStr = "android.resource://$packageName/$resId"
                list.add(RadioJingleSource(name, uriStr))
            }
        }

        // 2. Check fallback sn_jingle_1
        val fallbackResId = R.raw.sn_jingle_1
        if (list.isEmpty() && isValidRawMediaResource(fallbackResId)) {
            val uriStr = "android.resource://$packageName/$fallbackResId"
            list.add(RadioJingleSource("sn_jingle_1", uriStr))
        }

        return list
    }

    private fun getExternalRadioJingles(): List<RadioJingleSource> {
        val list = mutableListOf<RadioJingleSource>()
        val candidateDirs = listOf(
            File(context.filesDir, "SNjingle"),
            File(context.getExternalFilesDir(null), "SNjingle"),
            File(context.filesDir, "remote_assets/jingles"),
            File(context.getExternalFilesDir(null), "remote_assets/jingles")
        )

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    val lower = name.lowercase()
                    (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg"))
                }?.sortedBy { it.name } ?: emptyList()

                val validFiles = files.filter { it.length() >= MIN_MEDIA_FILE_SIZE_BYTES }
                for (file in validFiles) {
                    val nameWithoutExt = file.nameWithoutExtension
                    if (list.none { it.jingleName.equals(nameWithoutExt, ignoreCase = true) }) {
                        list.add(RadioJingleSource(nameWithoutExt, Uri.fromFile(file).toString()))
                    }
                }
            }
        }
        return list
    }

    private fun getTvStationIdSources(): List<StationIdSource> {
        val sources = mutableListOf<StationIdSource>()

        // 1. Check downloaded/local files on internal/external storage
        val candidateDirs = listOf(
            File(context.filesDir, "SNids"),
            File(context.getExternalFilesDir(null), "SNids"),
            File(context.filesDir, "SNid"),
            File(context.getExternalFilesDir(null), "SNid"),
            File(context.filesDir, "SNid_720"),
            File(context.getExternalFilesDir(null), "SNid_720"),
            File(context.filesDir, "remote_assets/tv_ids"),
            File(context.getExternalFilesDir(null), "remote_assets/tv_ids")
        )

        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    val lower = name.lowercase()
                    (lower.startsWith("sjsn") || lower.startsWith("sn_id") || lower.startsWith("sn_tv")) && (lower.endsWith(".mp4") || lower.endsWith(".mkv"))
                }?.sortedBy { it.name } ?: emptyList()

                val validFiles = files.filter { it.length() >= MIN_MEDIA_FILE_SIZE_BYTES }
                for (file in validFiles) {
                    val nameWithoutExt = file.nameWithoutExtension
                    if (sources.none { it.idName.equals(nameWithoutExt, ignoreCase = true) }) {
                        sources.add(StationIdSource(nameWithoutExt, Uri.fromFile(file).toString()))
                    }
                }
            }
        }

        // 2. Check bundled asset files in assets/SNid/
        try {
            val assetFiles = context.assets.list("SNid")?.filter { name ->
                val lower = name.lowercase()
                (lower.startsWith("sjsn") || lower.startsWith("sn_id") || lower.startsWith("sn_tv")) && (lower.endsWith(".mp4") || lower.endsWith(".mkv"))
            }?.sorted() ?: emptyList()

            for (assetName in assetFiles) {
                val nameWithoutExt = assetName.substringBeforeLast(".")
                if (sources.none { it.idName.equals(nameWithoutExt, ignoreCase = true) }) {
                    sources.add(StationIdSource(nameWithoutExt, "asset:///SNid/$assetName"))
                }
            }
        } catch (e: Exception) {
            AppLogger.d("StationContentManager", "No assets/SNid/ directory found or error reading assets: ${e.message}")
        }

        // 3. Fallback raw resource sn_id_1
        val resId = R.raw.sn_id_1
        if (sources.isEmpty() && isValidRawMediaResource(resId)) {
            val packageName = context.packageName
            sources.add(StationIdSource("sn_id_1", "android.resource://$packageName/$resId"))
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
