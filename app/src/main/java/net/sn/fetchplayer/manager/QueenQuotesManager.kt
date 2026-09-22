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
            "radio-red" -> Color.parseColor("#BF616A")    // Nord 11 Aurora Red (TV Badge / Accent)
            "radio-orange" -> Color.parseColor("#D08770") // Nord 12 Aurora Orange
            "radio-yellow" -> Color.parseColor("#EBCB8B") // Nord 13 Aurora Gold / Queen Yellow
            "radio-green" -> Color.parseColor("#A3BE8C")  // Nord 14 Aurora Green (Play-Pause Tint / Active Controls)
            "radio-purple" -> Color.parseColor("#B48EAD") // Nord 15 Aurora Purple
            "radio-frost" -> Color.parseColor("#8FBCBB")  // Nord 7 Frost Teal
            "radio-blue" -> Color.parseColor("#81A1C1")   // Nord 9 Ice Blue
            else -> Color.parseColor("#88C0D0")           // Nord 8 Primary Cyan (Main Player Control Accent)
        }
    }
}
