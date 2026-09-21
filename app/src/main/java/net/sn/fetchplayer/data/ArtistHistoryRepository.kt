package net.sn.fetchplayer.data

import net.sn.fetchplayer.ui.ArtistHistoryItem

object ArtistHistoryRepository {
    private val historyItems = mutableListOf<ArtistHistoryItem>()

    fun getHistory(): List<ArtistHistoryItem> {
        return historyItems.toList()
    }

    fun hasEntryForTrack(trackId: String, youtubeId: String?): Boolean {
        return historyItems.any {
            it.track.id == trackId ||
                    (youtubeId != null && it.track.youtubeId != null && it.track.youtubeId == youtubeId)
        }
    }

    fun addOrUpdateItem(newItem: ArtistHistoryItem) {
        val existingIndex = historyItems.indexOfFirst {
            it.track.id == newItem.track.id ||
                    (it.track.youtubeId != null && newItem.track.youtubeId != null && it.track.youtubeId == newItem.track.youtubeId)
        }
        if (existingIndex >= 0) {
            historyItems[existingIndex] = newItem
        } else {
            historyItems.add(0, newItem)
        }
    }

    fun clear() {
        historyItems.clear()
    }
}
