package net.sn.fetchplayer.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.sn.fetchplayer.manager.PlaylistManager.RawPlaylistItem
import net.sn.fetchplayer.util.AppLogger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages the Station North Mixtape Container format (.sncrate).
 * A .sncrate file is a standard compressed ZIP archive containing:
 *  - manifest.json: Metadata about the mixtape (title, curator, track count, timestamp)
 *  - playlist.json: Array of tracks with YouTube IDs, titles, and artists
 */
object CrateArchiveManager {

    private const val TAG = "CrateArchiveManager"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    data class CrateManifest(
        val formatVersion: Int = 1,
        val playlistId: String,
        val playlistName: String,
        val curatorName: String,
        val trackCount: Int,
        val createdAtEpochMs: Long = System.currentTimeMillis(),
        val appVersion: String = "1.0.0.48"
    )

    /**
     * Compresses a playlist into in-memory .sncrate ZIP bytes.
     */
    fun packCrateToBytes(
        playlist: CustomPlaylist,
        curatorName: String = "Station North Curator"
    ): ByteArray {
        val manifest = CrateManifest(
            playlistId = playlist.id,
            playlistName = playlist.name,
            curatorName = curatorName,
            trackCount = playlist.tracks.size
        )

        val manifestJson = gson.toJson(manifest)
        val playlistJson = gson.toJson(playlist.tracks)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            // 1. Add manifest.json
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 2. Add playlist.json
            zos.putNextEntry(ZipEntry("playlist.json"))
            zos.write(playlistJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val bytes = baos.toByteArray()
        AppLogger.d(TAG, "Packed crate '${playlist.name}' into ${bytes.size} bytes (compressed)")
        return bytes
    }

    /**
     * Packs a playlist into a temporary .sncrate file in cacheDir for sharing.
     */
    fun packCrateToFile(
        context: Context,
        playlist: CustomPlaylist,
        curatorName: String = "Station North Curator"
    ): File {
        val cratesDir = File(context.cacheDir, "shared_crates").apply { if (!exists()) mkdirs() }
        val safeName = playlist.name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40).ifEmpty { "mixtape" }
        val targetFile = File(cratesDir, "$safeName.sncrate")

        val bytes = packCrateToBytes(playlist, curatorName)
        FileOutputStream(targetFile).use { it.write(bytes) }
        AppLogger.d(TAG, "Saved crate to file: ${targetFile.absolutePath} (${bytes.size} bytes)")
        return targetFile
    }

    /**
     * Unpacks a .sncrate from in-memory byte array.
     */
    fun unpackCrateFromBytes(zipBytes: ByteArray): Pair<CrateManifest, List<RawPlaylistItem>>? {
        return try {
            var manifestJson: String? = null
            var playlistJson: String? = null

            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "manifest.json" -> manifestJson = zis.bufferedReader(Charsets.UTF_8).readText()
                        "playlist.json" -> playlistJson = zis.bufferedReader(Charsets.UTF_8).readText()
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (manifestJson == null || playlistJson == null) {
                AppLogger.e(TAG, "Crate archive missing required manifest.json or playlist.json")
                return null
            }

            val manifest = gson.fromJson(manifestJson, CrateManifest::class.java)
            val trackType = object : TypeToken<List<RawPlaylistItem>>() {}.type
            val tracks = gson.fromJson<List<RawPlaylistItem>>(playlistJson, trackType) ?: emptyList()

            AppLogger.d(TAG, "Successfully unpacked crate '${manifest.playlistName}' (${tracks.size} tracks)")
            Pair(manifest, tracks)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to unpack .sncrate bytes", e)
            null
        }
    }

    /**
     * Unpacks a .sncrate from a Content or File URI (e.g. from WhatsApp or File Manager).
     */
    fun unpackCrateFromUri(context: Context, uri: Uri): Pair<CrateManifest, List<RawPlaylistItem>>? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            unpackCrateFromBytes(bytes)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read crate from URI $uri", e)
            null
        }
    }

    /**
     * Builds an Android Sharesheet Intent for sharing the .sncrate file via WhatsApp, Telegram, etc.
     */
    fun createShareIntent(context: Context, crateFile: File, playlistTitle: String): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, crateFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Station North Mixtape: $playlistTitle")
            putExtra(
                Intent.EXTRA_TEXT,
                "Check out my Station North Mixtape: \"$playlistTitle\"!\n\nOpen this .sncrate file with SNfetchPLAYER to import and listen directly."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, "Share Mixtape via...")
    }
}
