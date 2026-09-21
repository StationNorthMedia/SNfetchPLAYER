package com.example.snfetchplayer.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.snfetchplayer.manager.PlaylistManager.RawPlaylistItem
import com.example.snfetchplayer.util.AppLogger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

data class CustomPlaylist(
    val id: String,
    var name: String,
    val tracks: MutableList<RawPlaylistItem> = mutableListOf()
)

class CuratedCatalogManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val prefs: SharedPreferences = context.getSharedPreferences("sn_playlists_prefs", Context.MODE_PRIVATE)
    private val playlistsDir: File = File(context.filesDir, "playlists").apply { if (!exists()) mkdirs() }
    private val legacyCatalogFile: File = File(context.filesDir, "curated_catalog.json")

    init {
        ensureDefaultPlaylistsExist()
    }

    private fun ensureDefaultPlaylistsExist() {
        // Seed Station North Main Mix (Top 100 R&B & Hip-Hop)
        val mainFile = File(playlistsDir, "default_main_top100.json")
        if (!mainFile.exists()) {
            val mainTracks = loadPresetFromAssets("main_top100.json")
            if (mainTracks.isNotEmpty()) {
                val mainPlaylist = CustomPlaylist(
                    id = "default_main_top100",
                    name = "Station North Main Mix (100 Hits)",
                    tracks = mainTracks.toMutableList()
                )
                savePlaylistInternal(mainPlaylist)
                AppLogger.d("CuratedCatalogManager", "Seeded default main playlist: Station North Main Mix (${mainTracks.size} tracks)")
            }
        }

        // Seed US R&B Top 50 preset if it does not exist yet
        val rnbFile = File(playlistsDir, "preset_rnb_top50.json")
        if (!rnbFile.exists()) {
            val rnbTracks = loadPresetFromAssets("us_rnb_top50.json")
            if (rnbTracks.isNotEmpty()) {
                val rnbPlaylist = CustomPlaylist(
                    id = "preset_rnb_top50",
                    name = "US R&B Top 50",
                    tracks = rnbTracks.toMutableList()
                )
                savePlaylistInternal(rnbPlaylist)
            }
        }

        // Seed US HipHop Top 50 preset if it does not exist yet
        val hiphopFile = File(playlistsDir, "preset_hiphop_top50.json")
        if (!hiphopFile.exists()) {
            val hiphopTracks = loadPresetFromAssets("us_hiphop_top50.json")
            if (hiphopTracks.isNotEmpty()) {
                val hiphopPlaylist = CustomPlaylist(
                    id = "preset_hiphop_top50",
                    name = "US HipHop Top 50",
                    tracks = hiphopTracks.toMutableList()
                )
                savePlaylistInternal(hiphopPlaylist)
            }
        }

        if (!prefs.contains("active_playlist_id")) {
            setActivePlaylistId("default_main_top100")
        }
    }

    fun loadPresetFromAssets(assetFileName: String = "main_top100.json"): List<RawPlaylistItem> {
        return try {
            val jsonString = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<RawPlaylistItem>>() {}.type
            gson.fromJson(jsonString, type) ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e("CuratedCatalogManager", "Failed to load preset $assetFileName from assets", e)
            emptyList()
        }
    }

    @Synchronized
    fun getPlaylists(): List<CustomPlaylist> {
        val files = playlistsDir.listFiles { _, name -> name.endsWith(".json") } ?: return emptyList()
        val playlists = mutableListOf<CustomPlaylist>()
        val type = object : TypeToken<CustomPlaylist>() {}.type

        for (file in files) {
            try {
                val json = file.readText()
                val playlist = gson.fromJson<CustomPlaylist>(json, type)
                if (playlist != null) {
                    playlists.add(playlist)
                }
            } catch (e: Exception) {
                AppLogger.e("CuratedCatalogManager", "Failed to read playlist file ${file.name}", e)
            }
        }
        playlists.sortBy { it.name.lowercase() }
        return playlists
    }

    fun getActivePlaylistId(): String {
        return prefs.getString("active_playlist_id", "default_main_top100") ?: "default_main_top100"
    }

    fun setActivePlaylistId(playlistId: String) {
        prefs.edit().putString("active_playlist_id", playlistId).apply()
    }

    @Synchronized
    fun getActivePlaylist(): CustomPlaylist {
        val activeId = getActivePlaylistId()
        val file = File(playlistsDir, "$activeId.json")
        if (file.exists()) {
            try {
                val type = object : TypeToken<CustomPlaylist>() {}.type
                val playlist = gson.fromJson<CustomPlaylist>(file.readText(), type)
                if (playlist != null) return playlist
            } catch (e: Exception) {
                AppLogger.e("CuratedCatalogManager", "Error reading active playlist: $activeId", e)
            }
        }
        val fallback = getPlaylists().firstOrNull() ?: CustomPlaylist("default_main_top100", "Station North Main Mix (100 Hits)", loadPresetFromAssets("main_top100.json").toMutableList())
        setActivePlaylistId(fallback.id)
        return fallback
    }

    @Synchronized
    fun createPlaylist(name: String, tracks: List<RawPlaylistItem> = emptyList()): CustomPlaylist {
        val newId = "playlist_" + UUID.randomUUID().toString().take(8)
        val playlist = CustomPlaylist(
            id = newId,
            name = name.ifEmpty { "New Playlist" },
            tracks = tracks.toMutableList()
        )
        savePlaylistInternal(playlist)
        setActivePlaylistId(newId)
        AppLogger.d("CuratedCatalogManager", "Created new playlist: ${playlist.name} [$newId]")
        return playlist
    }

    @Synchronized
    fun renamePlaylist(id: String, newName: String): Boolean {
        val playlist = getPlaylistById(id) ?: return false
        playlist.name = newName.ifEmpty { "Untitled Playlist" }
        return savePlaylistInternal(playlist)
    }

    @Synchronized
    fun deletePlaylist(id: String): Boolean {
        val playlists = getPlaylists()
        if (playlists.size <= 1) {
            AppLogger.w("CuratedCatalogManager", "Cannot delete the last remaining playlist.")
            return false
        }
        val file = File(playlistsDir, "$id.json")
        val deleted = file.delete()
        if (deleted && getActivePlaylistId() == id) {
            val remaining = getPlaylists().firstOrNull()
            if (remaining != null) {
                setActivePlaylistId(remaining.id)
            }
        }
        return deleted
    }

    private fun getPlaylistById(id: String): CustomPlaylist? {
        val file = File(playlistsDir, "$id.json")
        if (!file.exists()) return null
        return try {
            val type = object : TypeToken<CustomPlaylist>() {}.type
            gson.fromJson<CustomPlaylist>(file.readText(), type)
        } catch (e: Exception) {
            null
        }
    }

    private fun savePlaylistInternal(playlist: CustomPlaylist): Boolean {
        return try {
            val file = File(playlistsDir, "${playlist.id}.json")
            file.writeText(gson.toJson(playlist))
            true
        } catch (e: Exception) {
            AppLogger.e("CuratedCatalogManager", "Failed to save playlist ${playlist.id}", e)
            false
        }
    }

    @Synchronized
    fun getCuratedTracks(): List<RawPlaylistItem> {
        return getActivePlaylist().tracks
    }

    @Synchronized
    fun resetToDefaultCatalog(): List<RawPlaylistItem> {
        val active = getActivePlaylist()
        val defaultTracks = loadPresetFromAssets("us_rnb_top50.json")
        active.tracks.clear()
        active.tracks.addAll(defaultTracks)
        savePlaylistInternal(active)
        AppLogger.d("CuratedCatalogManager", "Reset playlist '${active.name}' to default Top 50 (${defaultTracks.size} tracks)")
        return defaultTracks
    }

    @Synchronized
    fun replaceCatalog(newTracks: List<RawPlaylistItem>): Boolean {
        val active = getActivePlaylist()
        active.tracks.clear()
        active.tracks.addAll(newTracks)
        return savePlaylistInternal(active)
    }

    @Synchronized
    fun appendCatalog(newTracks: List<RawPlaylistItem>): List<RawPlaylistItem> {
        val active = getActivePlaylist()
        val existingIds = active.tracks.map { it.youtubeId }.toSet()
        var addedCount = 0
        for (track in newTracks) {
            if (!existingIds.contains(track.youtubeId)) {
                active.tracks.add(track)
                addedCount++
            }
        }
        savePlaylistInternal(active)
        AppLogger.d("CuratedCatalogManager", "Appended $addedCount tracks to playlist '${active.name}'")
        return active.tracks
    }

    @Synchronized
    fun saveCatalog(tracks: List<RawPlaylistItem>): Boolean {
        return replaceCatalog(tracks)
    }

    @Synchronized
    fun isSaved(youtubeId: String): Boolean {
        return getCuratedTracks().any { it.youtubeId == youtubeId }
    }

    @Synchronized
    fun addTrack(youtubeId: String, title: String, artist: String): Boolean {
        val active = getActivePlaylist()
        if (active.tracks.any { it.youtubeId == youtubeId }) return false
        active.tracks.add(RawPlaylistItem(youtubeId, title, artist))
        return savePlaylistInternal(active)
    }

    @Synchronized
    fun removeTrack(youtubeId: String): Boolean {
        val active = getActivePlaylist()
        val removed = active.tracks.removeAll { it.youtubeId == youtubeId }
        if (removed) {
            savePlaylistInternal(active)
        }
        return removed
    }

    @Synchronized
    fun updateTrack(youtubeId: String, newTitle: String, newArtist: String): Boolean {
        val active = getActivePlaylist()
        val index = active.tracks.indexOfFirst { it.youtubeId == youtubeId }
        if (index != -1) {
            active.tracks[index] = RawPlaylistItem(youtubeId, newTitle, newArtist)
            return savePlaylistInternal(active)
        }
        return false
    }

    @Synchronized
    fun getJsonContent(): String {
        val active = getActivePlaylist()
        return gson.toJson(active.tracks)
    }

    fun getFilePath(): String {
        val activeId = getActivePlaylistId()
        return File(playlistsDir, "$activeId.json").absolutePath
    }
}
