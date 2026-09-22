package net.sn.fetchplayer.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import net.sn.fetchplayer.R

class QueenTutorialDialog(context: Context) : Dialog(context) {

    private lateinit var tvChapterContent: TextView
    private lateinit var toggleChapterGroup: MaterialButtonToggleGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_queen_tutorial)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        tvChapterContent = findViewById(R.id.tvChapterContent)
        toggleChapterGroup = findViewById(R.id.toggleChapterGroup)

        findViewById<MaterialButton>(R.id.btnCloseTutorial).setOnClickListener { dismiss() }
        findViewById<MaterialButton>(R.id.btnGotIt).setOnClickListener { dismiss() }

        toggleChapterGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnChapterCurator -> showChapter(1)
                    R.id.btnChapterBroadcast -> showChapter(2)
                    R.id.btnChapterLexicon -> showChapter(3)
                    R.id.btnChapterProTips -> showChapter(4)
                }
            }
        }

        // Show Chapter 1 by default
        showChapter(1)
    }

    private fun showChapter(chapterIndex: Int) {
        val content = when (chapterIndex) {
            1 -> """
                👑 CHAPTER 1: MASTERING THE CURATOR STUDIO
                
                "Listen up, sunshine. The Curator Studio is your master command deck where you build and shape your station frequency."
                
                📥 1. IMPORTING PLAYLISTS & CATALOGS:
                • Paste any YouTube Playlist URL into the input field and press 'Import' or hit ENTER.
                • Tap '📥 Import JSON' to load custom catalog files or saved Station North playlists.
                
                📋 2. ACTIVE QUEUE vs. SAVED CATALOG:
                • Use the Curator Sub-Tab buttons to switch between your current active Queue and your permanent Saved Catalog.
                • Tap '💾 Save Catalog' to export your curated playlist as a shareable JSON file.
                
                ✏️ 3. EDIT TRACK & METADATA:
                • Tap the '✏️' icon on any track to open the Track Editor. Adjust artist names, track titles, or custom tags on-the-fly.
                • Tap '🗑️' to instantly remove tracks from your list.
                
                🔀 4. REORDERING & CATALOG RESETS:
                • Drag and drop or use queue actions to reorder songs.
                • Tap 'Reset Top 50' to restore default Queen's Choice playlists anytime!
            """.trimIndent()

            2 -> """
                📺 CHAPTER 2: BROADCAST DECK & 2:1 ROTATION ENGINE
                
                "Station North TV & Radio aren't standard media players—they are real broadcast streams."
                
                🔄 1. THE IMMUTABLE 2:1 ROTATION ENGINE:
                • Every 2 YouTube music tracks are automatically followed by 1 Station ID or Jingle.
                • This guarantees genuine broadcast feel without manual intervention.
                
                📺 2. 16:9 TV MODE vs. SN-RADIO MODE:
                • SN-TV: Plays 16:9 video content with side-by-side Wikipedia feed.
                • SN-RADIO: Switches to real-time FFT spectrum visualizers with beat-synced artist portraits!
                
                📖 3. DYNAMIC WIKIPEDIA FEED:
                • Automatically fetches and displays English Wikipedia bios and high-res thumbnails for currently playing artists.
            """.trimIndent()

            3 -> """
                📖 CHAPTER 3: THE SN-LEXIKON ARCHIVE
                
                "Explore 4,438+ Urban, R&B, and Hip-Hop legends indexed straight into the application."
                
                🔍 1. INSTANT SEARCH:
                • Type any artist, album, or song title into the Lexicon search bar to view complete discographies and biographies.
                
                🎲 2. RANDOM ARTIST DISCOVERY:
                • Tap '🎲 Random' to pick a random legend from the archive and explore their full discography.
                
                ▶️ 3. ONE-TAP YOUTUBE STREAMING:
                • Tap any album or track inside an artist's discography to immediately search and play it on YouTube.
            """.trimIndent()

            4 -> """
                ⚡ CHAPTER 4: PRO TIPS & TV OPTIMIZATION
                
                "Here are Her Majesty's insider secrets for maximum performance:"
                
                💾 1. OFFLINE MEDIA & BIO CACHING (SETTINGS):
                • Enable the Cache toggle in SETTINGS to save Wikipedia bios and images locally on your device.
                • IMPORTANT: Leave disabled on Android TVs with limited storage!
                
                📡 2. REMOTE ASSET CLOUD SYNC:
                • Tap '🔄 Sync Now' in SETTINGS to download the latest Station IDs, Jingles, and quotes from our servers.
                
                🖥️ 3. TRUE FULLSCREEN TV MODE:
                • Tap the Fullscreen button or press your TV remote OK button to expand video output to 100% full screen.
            """.trimIndent()

            else -> ""
        }

        tvChapterContent.text = content
    }
}
