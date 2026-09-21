package net.sn.fetchplayer.manager

import android.content.Context
import android.graphics.Color
import net.sn.fetchplayer.util.AppLogger
import org.json.JSONArray
import kotlin.random.Random

data class QueenQuote(
    val text: String,
    val colorClass: String,
    val colorInt: Int
)

object QueenQuotesManager {

    private val quotesList = mutableListOf<QueenQuote>()
    private var lastIndex = -1

    fun init(context: Context) {
        if (quotesList.isNotEmpty()) return

        try {
            val jsonString = context.assets.open("sprueche.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val text = obj.optString("text", "")
                val colorClass = obj.optString("class", obj.optString("color", "radio-yellow"))
                val colorInt = parseAmbientColor(colorClass)

                if (text.isNotEmpty()) {
                    quotesList.add(QueenQuote(text, colorClass, colorInt))
                }
            }
            AppLogger.d("QueenQuotesManager", "Loaded ${quotesList.size} Queen Quotes from assets/sprueche.json")
        } catch (e: Exception) {
            AppLogger.e("QueenQuotesManager", "Failed to load sprueche.json", e)
        }
    }

    @Synchronized
    fun getRandomQuote(): QueenQuote? {
        if (quotesList.isEmpty()) return null
        if (quotesList.size == 1) return quotesList[0]

        var nextIndex: Int
        do {
            nextIndex = Random.nextInt(quotesList.size)
        } while (nextIndex == lastIndex)

        lastIndex = nextIndex
        return quotesList[nextIndex]
    }

    fun getChroniclesText(context: Context): String {
        return try {
            context.assets.open("manjaro_chronicles.txt").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            AppLogger.e("QueenQuotesManager", "Failed to load manjaro_chronicles.txt", e)
            "THE MANJARO LOUNGE CHRONICLES\n\nChapter 1: The Gray V6..."
        }
    }

    private fun parseAmbientColor(colorClass: String): Int {
        return when (colorClass.lowercase()) {
            "radio-red" -> Color.parseColor("#FF5252")    // Bright Neon Red / Nord 11 accent
            "radio-orange" -> Color.parseColor("#FF9800") // Warm Orange / Nord 12 accent
            "radio-yellow" -> Color.parseColor("#FFD54F") // Gold Yellow / Nord 13 accent
            "radio-green" -> Color.parseColor("#69F0AE")  // Emerald Green / Nord 14 accent
            "radio-purple" -> Color.parseColor("#E040FB") // Velvet Purple / Nord 15 accent
            else -> Color.parseColor("#88C0D0")           // Default Nord Cyan
        }
    }
}
