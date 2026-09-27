package net.sn.fetchplayer.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.sn.fetchplayer.model.ChronicleCardItem
import net.sn.fetchplayer.util.AppLogger

object ChroniclesRepository {

    private const val TAG = "ChroniclesRepository"
    private const val JSON_PATH = "comic/chronicles_data.json"

    fun loadChroniclesData(context: Context): List<ChronicleCardItem> {
        return try {
            val jsonString = context.assets.open(JSON_PATH).bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<ChronicleCardItem>>() {}.type
            val items: List<ChronicleCardItem> = Gson().fromJson(jsonString, type)
            AppLogger.d(TAG, "Successfully loaded ${items.size} chronicle items from $JSON_PATH")
            items
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load $JSON_PATH", e)
            emptyList()
        }
    }
}
