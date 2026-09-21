package com.example.snfetchplayer.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.audiofx.Visualizer
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.snfetchplayer.R
import com.example.snfetchplayer.databinding.ActivityMainBinding
import android.widget.ImageButton
import android.widget.LinearLayout
import com.example.snfetchplayer.data.ArtistLexiconRepository
import com.example.snfetchplayer.data.LexiconArtist
import com.example.snfetchplayer.data.WikipediaArtistFetcher
import com.example.snfetchplayer.extractor.YouTubePlaylistExtractor
import com.example.snfetchplayer.manager.CuratedCatalogManager
import com.example.snfetchplayer.manager.PlaylistManager.RawPlaylistItem
import com.example.snfetchplayer.model.PlaybackMode
import com.example.snfetchplayer.model.Track
import com.example.snfetchplayer.service.RadioService
import com.example.snfetchplayer.util.AppLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), RadioService.ServiceListener {

    private lateinit var binding: ActivityMainBinding
    private var radioService: RadioService? = null
    private var isBound = false
    private var isFullscreen = false

    private var audioVisualizerFx: Visualizer? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var bauchbindeRunnable: Runnable? = null
    private var hudTimeoutRunnable: Runnable? = null

    private lateinit var curatedCatalogManager: CuratedCatalogManager
    private lateinit var curatorAdapter: CuratorPlaylistAdapter
    private lateinit var artistHistoryAdapter: ArtistHistoryAdapter
    private lateinit var lexiconSearchDropdownAdapter: LexiconSearchDropdownAdapter
    private var currentLexiconArtist: LexiconArtist? = null
    private var isSettingsModeActive = false
    private var isShowingSavedCatalogTab = false

    private val importedPlaylistItems = mutableListOf<RawPlaylistItem>()

    private val exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val jsonStr = curatedCatalogManager.getJsonContent()
                    outputStream.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "JSON exported successfully!", Toast.LENGTH_SHORT).show()
                AppLogger.d("MainActivity", "Exported JSON catalog to URI: $uri")
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Failed to export JSON to SAF URI", e)
                Toast.makeText(this, "Error exporting file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportLogsTxtLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    val logs = AppLogger.getLogHistory()
                    outputStream.write(logs.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Terminal logs saved to file!", Toast.LENGTH_SHORT).show()
                AppLogger.d("MainActivity", "Saved terminal logs to URI: $uri")
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Failed to save terminal logs to file", e)
                Toast.makeText(this, "Failed to save log file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!jsonString.isNullOrBlank()) {
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<List<RawPlaylistItem>>() {}.type
                    val importedItems: List<RawPlaylistItem>? = gson.fromJson(jsonString, type)
                    if (!importedItems.isNullOrEmpty()) {
                        showImportChoiceDialog(importedItems)
                    } else {
                        Toast.makeText(this, "No valid tracks found in JSON file!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Failed to import JSON from SAF URI", e)
                Toast.makeText(this, "Error reading JSON file.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as RadioService.RadioBinder
            val srv = binder.getService()
            radioService = srv
            isBound = true

            binding.playerView.player = srv.player
            srv.setServiceListener(this@MainActivity)

            updateUiMode(srv.currentMode)
            updateRepeatButtonState(srv.isRepeatOneActive)
            setupAudioSessionVisualizer()

            AppLogger.d("MainActivity", "Service connected and bound successfully")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            releaseAudioVisualizer()
            radioService = null
            AppLogger.d("MainActivity", "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.tvAppVersion.text = "Version ${com.example.snfetchplayer.BuildConfig.VERSION_NAME} • Station North Media"

        curatedCatalogManager = CuratedCatalogManager(this)
        com.example.snfetchplayer.data.ArtistLexiconRepository.init(this)
        com.example.snfetchplayer.manager.QueenQuotesManager.init(this)

        val initialSyncState = com.example.snfetchplayer.manager.RemoteAssetSyncManager.checkLocalAssetsStatus(this)
        updateAssetSyncUi(initialSyncState)

        com.example.snfetchplayer.manager.RemoteAssetSyncManager.startRemoteAssetSync(this) { progress ->
            updateAssetSyncUi(progress)
        }

        if (savedInstanceState != null) {
            isSettingsModeActive = savedInstanceState.getBoolean("isSettingsModeActive", false)
            isShowingSavedCatalogTab = savedInstanceState.getBoolean("isShowingSavedCatalogTab", false)
            isFullscreen = savedInstanceState.getBoolean("isFullscreen", false)
            @Suppress("UNCHECKED_CAST")
            val savedItems = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("importedPlaylistItems", ArrayList::class.java) as? ArrayList<RawPlaylistItem>
            } else {
                @Suppress("DEPRECATION")
                savedInstanceState.getSerializable("importedPlaylistItems") as? ArrayList<RawPlaylistItem>
            }
            if (savedItems != null) {
                importedPlaylistItems.clear()
                importedPlaylistItems.addAll(savedItems)
            }
        }

        if (savedInstanceState == null) {
            setupKineticSplashScreen()
        } else {
            binding.layoutSplashOverlay.visibility = View.GONE
        }
        setupTerminalLogger()
        setupBauchbindeDesign()
        setupCuratorStudio()
        setupArtistHistoryRecyclerView()
        setupSettingsHub()
        checkPermissions()
        setupListeners()
        setupTvFocusEffects()
        restoreSavedUiState()
        startAndBindService()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isSettingsModeActive", isSettingsModeActive)
        outState.putBoolean("isShowingSavedCatalogTab", isShowingSavedCatalogTab)
        outState.putBoolean("isFullscreen", isFullscreen)
        outState.putSerializable("importedPlaylistItems", ArrayList(importedPlaylistItems))
    }

    private fun restoreSavedUiState() {
        if (isSettingsModeActive) {
            binding.toggleModeGroup.check(R.id.btnModeSettings)
            binding.cardSettingsContainer.visibility = View.VISIBLE
            binding.cardArtistInfoContainer.visibility = View.GONE
            binding.cardPlayerContainer.visibility = View.GONE
            binding.cardTrackInfo.visibility = View.GONE
            if (isShowingSavedCatalogTab) {
                binding.toggleCuratorTabGroup.check(R.id.btnTabSaved)
                binding.layoutPlaylistInputRow.visibility = View.GONE
                curatorAdapter.isSavedCatalogView = true
                val savedTracks = curatedCatalogManager.getCuratedTracks().map { RawPlaylistItem(it.youtubeId, it.title, it.artist) }
                curatorAdapter.setItems(savedTracks)
            } else {
                binding.toggleCuratorTabGroup.check(R.id.btnTabImported)
                binding.layoutPlaylistInputRow.visibility = View.GONE // Wait, imported tab shows layoutPlaylistInputRow in Curator Studio
                binding.layoutPlaylistInputRow.visibility = View.VISIBLE
                curatorAdapter.isSavedCatalogView = false
                curatorAdapter.setItems(importedPlaylistItems)
            }
            updateCuratorStatusCount()
        }
        if (isFullscreen) {
            enterTrueFullscreen()
        }
    }

    private fun setupCuratorStudio() {
        val savedIds = curatedCatalogManager.getCuratedTracks().map { it.youtubeId }.toMutableSet()
        curatorAdapter = CuratorPlaylistAdapter(
            items = mutableListOf(),
            savedYtIds = savedIds,
            isSavedCatalogView = false,
            onPlayClick = { item ->
                AppLogger.d("MainActivity", "Curator selected track: '${item.title}' [${item.youtubeId}]")
                radioService?.playCuratorTrack(item)
                curatorAdapter.updatePlayingId(item.youtubeId)
            },
            onSaveClick = { item ->
                saveTrackToCuratedJson(item)
            },
            onEditClick = { item, pos ->
                showEditTrackDialog(item, pos)
            },
            onRemoveClick = { item, pos ->
                if (isShowingSavedCatalogTab) {
                    val removed = curatedCatalogManager.removeTrack(item.youtubeId)
                    if (removed) {
                        curatorAdapter.removeItemAt(pos)
                        curatorAdapter.markRemoved(item.youtubeId)
                        updateCuratorStatusCount()
                        syncMasterCatalogWithRadioService()
                        Toast.makeText(this, "Removed from playlist", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    curatorAdapter.removeItemAt(pos)
                    updateCuratorStatusCount()
                    Toast.makeText(this, "Removed track from import queue", Toast.LENGTH_SHORT).show()
                }
            }
        )

        binding.rvCuratorPlaylist.layoutManager = LinearLayoutManager(this)
        binding.rvCuratorPlaylist.adapter = curatorAdapter

        updateCuratorStatusCount()
    }

    private fun showEditTrackDialog(item: RawPlaylistItem, position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_track, null)
        val tvYtId = dialogView.findViewById<TextView>(R.id.tvEditYtId)
        val etTitle = dialogView.findViewById<EditText>(R.id.etEditTitle)
        val etArtist = dialogView.findViewById<EditText>(R.id.etEditArtist)

        tvYtId.text = "YouTube ID: ${item.youtubeId}"
        etTitle.setText(item.title)
        etArtist.setText(item.artist)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = etTitle.text.toString().trim()
                val newArtist = etArtist.text.toString().trim()
                if (newTitle.isNotEmpty() && newArtist.isNotEmpty()) {
                    val updated = curatedCatalogManager.updateTrack(item.youtubeId, newTitle, newArtist)
                    if (updated) {
                        val updatedItem = RawPlaylistItem(item.youtubeId, newTitle, newArtist)
                        curatorAdapter.updateItemAt(position, updatedItem)
                        syncMasterCatalogWithRadioService()
                        Toast.makeText(this, "Track metadata updated!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveTrackToCuratedJson(item: RawPlaylistItem) {
        val success = curatedCatalogManager.addTrack(
            youtubeId = item.youtubeId,
            title = item.title,
            artist = item.artist
        )
        if (success) {
            curatorAdapter.markSaved(item.youtubeId)
            updateCuratorStatusCount()
            syncMasterCatalogWithRadioService()
            Toast.makeText(this, "✓ '${item.title}' saved to playlist!", Toast.LENGTH_SHORT).show()
            AppLogger.d("MainActivity", "CURATOR -> Saved '${item.title}' to playlist")
        } else {
            Toast.makeText(this, "'${item.title}' is already in this playlist!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncMasterCatalogWithRadioService() {
        val currentCatalog = curatedCatalogManager.getCuratedTracks()
        radioService?.playlistManager?.updateMasterCatalog(currentCatalog)
        AppLogger.d("MainActivity", "Synced master catalog with RadioService (${currentCatalog.size} tracks)")
    }

    private fun updateCuratorStatusCount() {
        val loadedCount = importedPlaylistItems.size
        val activePlaylist = curatedCatalogManager.getActivePlaylist()
        val savedCount = activePlaylist.tracks.size
        binding.btnTabImported.text = "Import Queue ($loadedCount)"
        binding.btnTabSaved.text = "✓ ${activePlaylist.name} ($savedCount)"

        val currentCount = curatorAdapter.itemCount
        binding.tvCuratorStatusCount.text = if (isShowingSavedCatalogTab) "$currentCount in Playlist" else "$currentCount Loaded"
    }

    private fun updateCuratorCatalogView() {
        if (isShowingSavedCatalogTab) {
            val savedTracks = curatedCatalogManager.getCuratedTracks()
            curatorAdapter.setItems(savedTracks)
        } else {
            curatorAdapter.setItems(importedPlaylistItems)
        }
        updateCuratorStatusCount()
    }

    private fun showPlaylistManagerDialog() {
        val playlists = curatedCatalogManager.getPlaylists()
        val activeId = curatedCatalogManager.getActivePlaylistId()
        val options = playlists.map { playlist ->
            val activePrefix = if (playlist.id == activeId) "▶️ [ACTIVE] " else "📁 "
            "$activePrefix${playlist.name} (${playlist.tracks.size} tracks)"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("📁 Playlist Library")
            .setItems(options) { _, index ->
                val selected = playlists[index]
                curatedCatalogManager.setActivePlaylistId(selected.id)
                updateCuratorCatalogView()
                syncMasterCatalogWithRadioService()
                Toast.makeText(this, "Switched active playlist to '${selected.name}'", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("+ New Playlist") { _, _ ->
                showCreatePlaylistDialog()
            }
            .setNeutralButton("Manage Active") { _, _ ->
                showManagePlaylistOptionsDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(this).apply {
            hint = "Playlist Name"
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(this)
            .setTitle("Create New Playlist")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val created = curatedCatalogManager.createPlaylist(name)
                    updateCuratorCatalogView()
                    syncMasterCatalogWithRadioService()
                    Toast.makeText(this, "Created playlist '${created.name}'", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showManagePlaylistOptionsDialog() {
        val active = curatedCatalogManager.getActivePlaylist()
        val options = arrayOf("✏️ Rename '${active.name}'", "🗑️ Delete '${active.name}'")
        AlertDialog.Builder(this)
            .setTitle("Manage '${active.name}'")
            .setItems(options) { _, which ->
                if (which == 0) {
                    val input = EditText(this).apply {
                        setText(active.name)
                        setPadding(32, 24, 32, 24)
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Rename Playlist")
                        .setView(input)
                        .setPositiveButton("Save") { _, _ ->
                            val newName = input.text.toString().trim()
                            if (newName.isNotEmpty()) {
                                curatedCatalogManager.renamePlaylist(active.id, newName)
                                updateCuratorCatalogView()
                                Toast.makeText(this, "Renamed playlist to '$newName'", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else if (which == 1) {
                    AlertDialog.Builder(this)
                        .setTitle("Delete Playlist?")
                        .setMessage("Are you sure you want to delete '${active.name}'?")
                        .setPositiveButton("Delete") { _, _ ->
                            if (curatedCatalogManager.deletePlaylist(active.id)) {
                                updateCuratorCatalogView()
                                syncMasterCatalogWithRadioService()
                                Toast.makeText(this, "Deleted playlist.", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Cannot delete the last remaining playlist.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupBauchbindeDesign() {
        val density = resources.displayMetrics.density
        val nord8Color = ContextCompat.getColor(this, R.color.nord8)
        val glassBgColor = android.graphics.Color.parseColor("#E62E3440")

        binding.tvBauchbindeBadge.background = SlantedBackgroundDrawable(
            fillColor = nord8Color,
            slantWidthDp = 10f,
            density = density,
            slantSide = SlantSide.LEFT,
            slantInverted = true
        )

        binding.layoutBauchbindeContent.background = SlantedBackgroundDrawable(
            fillColor = glassBgColor,
            strokeColor = nord8Color,
            strokeWidthPx = 1.5f * density,
            slantWidthDp = 14f,
            density = density,
            slantSide = SlantSide.LEFT,
            slantInverted = true
        )
    }

    private fun setupKineticSplashScreen() {
        binding.layoutSplashOverlay.visibility = View.VISIBLE
        mainHandler.postDelayed({
            val fadeOut = AnimationUtils.loadAnimation(this, R.anim.fade_out)
            binding.layoutSplashOverlay.startAnimation(fadeOut)
            binding.layoutSplashOverlay.visibility = View.GONE
            AppLogger.d("MainActivity", "Kinetic Splash Screen intro completed")
        }, 2200)
    }

    private fun setupTerminalLogger() {
        AppLogger.d("MainActivity", "Terminal logger active")
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsNeeded.toTypedArray(),
                101
            )
        }
    }

    private fun setupAudioSessionVisualizer() {
        val srv = radioService ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AppLogger.d("MainActivity", "RECORD_AUDIO permission required for Real FFT Audio Visualizer")
            return
        }

        try {
            val audioSessionId = srv.player.audioSessionId
            if (audioSessionId != 0 && audioVisualizerFx == null) {
                audioVisualizerFx = Visualizer(audioSessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                        override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                            if (fft != null && srv.currentMode == PlaybackMode.SN_RADIO) {
                                binding.audioVisualizer.updateFft(fft)
                            }
                        }
                    }, Visualizer.getMaxCaptureRate() / 2, false, true)
                    enabled = true
                }
                AppLogger.d("MainActivity", "Real Android FFT Visualizer attached (session $audioSessionId)")
            }
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to initialize Real Android Visualizer API", e)
        }
    }

    private fun releaseAudioVisualizer() {
        try {
            audioVisualizerFx?.enabled = false
            audioVisualizerFx?.release()
            audioVisualizerFx = null
        } catch (_: Exception) {}
    }

    private fun setupListeners() {
        binding.toggleModeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnModeTv -> {
                        isSettingsModeActive = false
                        AppLogger.d("MainActivity", "Switched UI mode to SN-TV (16:9 HD)")
                        radioService?.setPlaybackMode(PlaybackMode.SN_TV)
                        updateUiMode(PlaybackMode.SN_TV)
                    }
                    R.id.btnModeRadio -> {
                        isSettingsModeActive = false
                        AppLogger.d("MainActivity", "Switched UI mode to SN-RADIO")
                        radioService?.setPlaybackMode(PlaybackMode.SN_RADIO)
                        updateUiMode(PlaybackMode.SN_RADIO)
                    }
                    R.id.btnModeSettings -> {
                        isSettingsModeActive = true
                        AppLogger.d("MainActivity", "Switched UI mode to SETTINGS HUB")
                        radioService?.player?.pause()
                        updateUiMode(radioService?.currentMode ?: PlaybackMode.SN_TV)
                    }
                }
            }
        }

        // Curator Sub-Tab Switcher: [ Import-Arbeitsliste ] vs [ Gespeicherter Katalog ]
        binding.toggleCuratorTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTabImported -> {
                        isShowingSavedCatalogTab = false
                        binding.layoutPlaylistInputRow.visibility = View.VISIBLE
                        curatorAdapter.isSavedCatalogView = false
                        curatorAdapter.setItems(importedPlaylistItems)
                        updateCuratorStatusCount()
                    }
                    R.id.btnTabSaved -> {
                        isShowingSavedCatalogTab = true
                        binding.layoutPlaylistInputRow.visibility = View.GONE
                        curatorAdapter.isSavedCatalogView = true
                        val savedTracks = curatedCatalogManager.getCuratedTracks()
                        curatorAdapter.setItems(savedTracks)
                        updateCuratorStatusCount()
                    }
                }
            }
        }

        binding.btnTabSaved.setOnClickListener {
            if (isShowingSavedCatalogTab) {
                showPlaylistManagerDialog()
            }
        }

        binding.fabPrevious.setOnClickListener { radioService?.playPreviousTrack() }
        binding.hudPrevious.setOnClickListener {
            radioService?.playPreviousTrack()
            showHudTemporarily()
        }
        binding.btnCuratorPrev.setOnClickListener { radioService?.playPreviousTrack() }

        binding.fabPlayPause.setOnClickListener { radioService?.togglePlayPause() }
        binding.hudPlayPause.setOnClickListener {
            radioService?.togglePlayPause()
            showHudTemporarily()
        }
        binding.btnCuratorPlayPause.setOnClickListener { radioService?.togglePlayPause() }

        binding.fabNext.setOnClickListener { radioService?.playNextTrack() }
        binding.hudNext.setOnClickListener {
            radioService?.playNextTrack()
            showHudTemporarily()
        }
        binding.btnCuratorNext.setOnClickListener { radioService?.playNextTrack() }

        binding.fabRepeat.setOnClickListener { radioService?.toggleRepeatOne() }
        binding.hudRepeat.setOnClickListener {
            radioService?.toggleRepeatOne()
            showHudTemporarily()
        }
        binding.btnCuratorRepeat.setOnClickListener { radioService?.toggleRepeatOne() }

        binding.btnFullscreenToggle.setOnClickListener { toggleFullscreen() }
        binding.btnCuratorFullscreen.setOnClickListener { toggleFullscreen() }
        binding.btnExitFullscreen.setOnClickListener { exitTrueFullscreen() }

        binding.playerView.setOnClickListener {
            if (isFullscreen) {
                showHudTemporarily()
            }
        }
        binding.curatorPlayerView.setOnClickListener {
            if (isFullscreen) {
                showHudTemporarily()
            }
        }
        binding.layoutRadioOverlay.setOnClickListener {
            if (isFullscreen) {
                showHudTemporarily()
            }
        }
        binding.audioVisualizer.setOnClickListener {
            if (isFullscreen) {
                showHudTemporarily()
            }
        }
        binding.cardRadioWikipediaPanel.setOnClickListener {
            if (isFullscreen) {
                showHudTemporarily()
            }
        }

        binding.audioVisualizer.setOnBassPulseListener(object : NordAudioVisualizerView.OnBassPulseListener {
            override fun onBassPulse(amplitude: Float) {
                val scale = 1.0f + (amplitude * 0.15f)
                binding.cardRadioArtistIconContainer.scaleX = scale
                binding.cardRadioArtistIconContainer.scaleY = scale
            }
        })

        setupButtonTouchEffects()

        // Curator Playlist Load Button
        binding.btnLoadPlaylist.setOnClickListener {
            val input = binding.etPlaylistInput.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(this, "Bitte YouTube-Playlist URL oder ID eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.progressLoadPlaylist.visibility = View.VISIBLE
            binding.btnLoadPlaylist.isEnabled = false

            lifecycleScope.launch(Dispatchers.IO) {
                AppLogger.d("MainActivity", "CURATOR -> Fetching YouTube playlist: $input")
                val tracks = YouTubePlaylistExtractor.fetchPlaylistTracks(input)
                withContext(Dispatchers.Main) {
                    binding.progressLoadPlaylist.visibility = View.GONE
                    binding.btnLoadPlaylist.isEnabled = true

                    if (tracks.isNotEmpty()) {
                        importedPlaylistItems.clear()
                        importedPlaylistItems.addAll(tracks)

                        binding.toggleCuratorTabGroup.check(R.id.btnTabImported)
                        curatorAdapter.isSavedCatalogView = false
                        curatorAdapter.setItems(importedPlaylistItems)
                        updateCuratorStatusCount()
                        Toast.makeText(this@MainActivity, "${tracks.size} tracks loaded from playlist!", Toast.LENGTH_SHORT).show()
                        AppLogger.d("MainActivity", "CURATOR -> Successfully loaded ${tracks.size} tracks into Curator UI")
                    } else {
                        Toast.makeText(this@MainActivity, "No tracks found or error loading playlist.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Curator Export JSON Button
        binding.btnExportJson.setOnClickListener {
            showExportJsonDialog()
        }

        // Curator Import JSON File Button (SAF)
        binding.btnImportJsonFile.setOnClickListener {
            importJsonLauncher.launch(arrayOf("application/json", "*/*"))
        }

        // Curator Reset Catalog Button (Reset to Top 50 default)
        binding.btnResetCatalog.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Catalog to Top 50?")
                .setMessage("Do you really want to reset the curated catalog to the default Top 50 R&B Hits?")
                .setPositiveButton("Reset") { _, _ ->
                    val defaultTracks = curatedCatalogManager.resetToDefaultCatalog()
                    if (isShowingSavedCatalogTab) {
                        curatorAdapter.setItems(defaultTracks)
                    }
                    updateCuratorStatusCount()
                    syncMasterCatalogWithRadioService()
                    Toast.makeText(this, "Catalog reset to Top 50!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Curator Clear List Button
        binding.btnClearCuratorList.setOnClickListener {
            if (isShowingSavedCatalogTab) {
                AlertDialog.Builder(this)
                    .setTitle("Clear Entire Catalog?")
                    .setMessage("Do you really want to delete all entries from the saved catalog?")
                    .setPositiveButton("Clear") { _, _ ->
                        curatedCatalogManager.replaceCatalog(emptyList())
                        curatorAdapter.setItems(emptyList())
                        updateCuratorStatusCount()
                        syncMasterCatalogWithRadioService()
                        Toast.makeText(this, "Saved catalog cleared.", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                importedPlaylistItems.clear()
                curatorAdapter.setItems(emptyList())
                updateCuratorStatusCount()
                Toast.makeText(this, "Import queue cleared.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showExportJsonDialog() {
        val jsonStr = curatedCatalogManager.getJsonContent()
        val filePath = curatedCatalogManager.getFilePath()
        val count = curatedCatalogManager.getCuratedTracks().size

        val message = "Saved JSON File ($count entries):\n$filePath\n\n$jsonStr"

        AlertDialog.Builder(this)
            .setTitle("Curated Catalog JSON")
            .setMessage(message)
            .setPositiveButton("Save File (SAF)") { _, _ ->
                exportJsonLauncher.launch("curated_catalog.json")
            }
            .setNeutralButton("Copy to Clipboard") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("curated_catalog.json", jsonStr)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "JSON copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showImportChoiceDialog(importedItems: List<RawPlaylistItem>) {
        AlertDialog.Builder(this)
            .setTitle("Import JSON File")
            .setMessage("${importedItems.size} tracks found in file.\n\nWould you like to append these tracks to the existing catalog or replace the current catalog?")
            .setPositiveButton("Append") { _, _ ->
                val updatedCatalog = curatedCatalogManager.appendCatalog(importedItems)
                if (isShowingSavedCatalogTab) {
                    curatorAdapter.setItems(updatedCatalog)
                }
                updateCuratorStatusCount()
                syncMasterCatalogWithRadioService()
                Toast.makeText(this, "${importedItems.size} tracks appended!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Replace") { _, _ ->
                curatedCatalogManager.replaceCatalog(importedItems)
                if (isShowingSavedCatalogTab) {
                    curatorAdapter.setItems(importedItems)
                }
                updateCuratorStatusCount()
                syncMasterCatalogWithRadioService()
                Toast.makeText(this, "Catalog replaced with ${importedItems.size} tracks!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonTouchEffects() {
        val buttons = listOf(
            binding.btnFullscreenToggle,
            binding.fabPrevious,
            binding.fabPlayPause,
            binding.fabNext,
            binding.fabRepeat,
            binding.btnExitFullscreen,
            binding.hudPrevious,
            binding.hudPlayPause,
            binding.hudNext,
            binding.hudRepeat
        )

        for (btn in buttons) {
            btn.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate()
                            .scaleX(0.92f)
                            .scaleY(0.92f)
                            .setDuration(100)
                            .start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(150)
                            .setInterpolator(OvershootInterpolator(1.5f))
                            .start()
                    }
                }
                false
            }
        }
    }

    private fun toggleFullscreen() {
        if (isFullscreen) {
            exitTrueFullscreen()
        } else {
            enterTrueFullscreen()
        }
    }

    private fun updateTabConsoleVisibility() {
        if (isFullscreen) {
            binding.cardPlayerContainer.visibility = View.VISIBLE
            binding.cardArtistInfoContainer.visibility = View.GONE
            binding.cardSettingsContainer.visibility = View.GONE
            return
        }

        if (isSettingsModeActive) {
            binding.cardArtistInfoContainer.visibility = View.GONE
            binding.cardPlayerContainer.visibility = View.GONE
            binding.cardTrackInfo.visibility = View.GONE
            binding.cardSettingsContainer.visibility = View.VISIBLE
        } else {
            binding.cardSettingsContainer.visibility = View.GONE
            binding.cardPlayerContainer.visibility = View.VISIBLE
            binding.cardTrackInfo.visibility = View.VISIBLE
            binding.cardArtistInfoContainer.visibility = View.VISIBLE
        }
    }

    private fun enterTrueFullscreen() {
        isFullscreen = true
        AppLogger.d("MainActivity", "Entered True Immersive Fullscreen Mode")

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.black))
        binding.rootLayout.setPadding(0, 0, 0, 0)
        binding.layoutHeader.visibility = View.GONE
        binding.toggleModeGroup.visibility = View.GONE
        binding.cardTrackInfo.visibility = View.GONE
        binding.layoutControls.visibility = View.GONE
        binding.cardSettingsContainer.visibility = View.GONE
        binding.cardArtistInfoContainer.visibility = View.GONE

        updateUiMode(radioService?.currentMode ?: PlaybackMode.SN_TV)

        // Remove card rounding and stroke in full screen for seamless 16:9 video edges
        binding.cardPlayerContainer.visibility = View.VISIBLE
        binding.cardPlayerContainer.radius = 0f
        binding.cardPlayerContainer.strokeWidth = 0

        val constraintSet = ConstraintSet()
        constraintSet.clone(binding.rootLayout)
        constraintSet.setDimensionRatio(R.id.cardPlayerContainer, "16:9")
        constraintSet.clear(R.id.cardPlayerContainer, ConstraintSet.END)
        constraintSet.connect(R.id.cardPlayerContainer, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP, 0)
        constraintSet.connect(R.id.cardPlayerContainer, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, 0)
        constraintSet.connect(R.id.cardPlayerContainer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
        constraintSet.connect(R.id.cardPlayerContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
        constraintSet.applyTo(binding.rootLayout)

        showHudTemporarily()
    }

    private fun exitTrueFullscreen() {
        isFullscreen = false
        AppLogger.d("MainActivity", "Exited Fullscreen Mode")

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())

        // Restore card rounding, stroke, and root padding in normal view
        val density = resources.displayMetrics.density
        val paddingPx = (12 * density).toInt()
        binding.rootLayout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        binding.cardPlayerContainer.radius = 12f * density
        binding.cardPlayerContainer.strokeWidth = (1.5f * density).toInt()

        binding.rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.nord0))
        binding.layoutHeader.visibility = View.VISIBLE
        binding.toggleModeGroup.visibility = View.VISIBLE
        binding.cardTrackInfo.visibility = View.VISIBLE
        binding.layoutControls.visibility = View.VISIBLE
        binding.layoutTouchHud.visibility = View.GONE

        val constraintSet = ConstraintSet()
        constraintSet.clone(this, R.layout.activity_main)
        constraintSet.applyTo(binding.rootLayout)

        updateUiMode(radioService?.currentMode ?: PlaybackMode.SN_TV)

        binding.layoutSplashOverlay.visibility = View.GONE
        binding.btnFullscreenToggle.setIconResource(R.drawable.ic_fullscreen)
    }

    private fun showHudTemporarily() {
        hudTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        binding.layoutTouchHud.visibility = View.VISIBLE

        hudTimeoutRunnable = Runnable {
            val fadeOut = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out)
            binding.layoutTouchHud.startAnimation(fadeOut)
            binding.layoutTouchHud.visibility = View.GONE
        }
        mainHandler.postDelayed(hudTimeoutRunnable!!, 3500)
    }

    private fun startAndBindService() {
        val intent = Intent(this, RadioService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    private fun updateUiMode(mode: PlaybackMode) {
        updatePlayerBinding()
        updateTabConsoleVisibility()

        if (isSettingsModeActive && !isFullscreen) {
            binding.toggleModeGroup.check(R.id.btnModeSettings)
            binding.audioVisualizer.setAudioPlaying(false)
        } else if (mode == PlaybackMode.SN_TV || (isSettingsModeActive && isFullscreen)) {
            binding.playerView.visibility = View.VISIBLE
            binding.layoutRadioOverlay.visibility = View.GONE
            binding.imgTvWatermark.visibility = if (isSettingsModeActive) View.GONE else View.VISIBLE
            if (!isSettingsModeActive) {
                binding.toggleModeGroup.check(R.id.btnModeTv)
            }
            binding.audioVisualizer.setAudioPlaying(false)
        } else {
            // SN_RADIO Mode
            binding.playerView.visibility = View.GONE
            binding.layoutRadioOverlay.visibility = View.VISIBLE
            binding.imgTvWatermark.visibility = View.GONE
            binding.toggleModeGroup.check(R.id.btnModeRadio)
            binding.audioVisualizer.setAudioPlaying(radioService?.player?.isPlaying == true)
            setupAudioSessionVisualizer()

            if (isFullscreen) {
                binding.cardRadioWikipediaPanel.visibility = View.VISIBLE
                binding.layoutRadioHeader.visibility = View.GONE
            } else {
                binding.cardRadioWikipediaPanel.visibility = View.GONE
                binding.layoutRadioHeader.visibility = View.VISIBLE
            }
        }

        if (isFullscreen) {
            binding.layoutHeader.visibility = View.GONE
            binding.toggleModeGroup.visibility = View.GONE
            binding.cardTrackInfo.visibility = View.GONE
            binding.layoutControls.visibility = View.GONE
        }
    }

    private fun updatePlayerBinding() {
        val player = radioService?.player
        if (isFullscreen) {
            binding.curatorPlayerView.player = null
            binding.playerView.player = player
        } else if (isSettingsModeActive) {
            binding.playerView.player = null
            binding.curatorPlayerView.player = player
        } else {
            binding.curatorPlayerView.player = null
            binding.playerView.player = player
        }
    }

    private fun triggerLowerThirdsBauchbinde(track: Track) {
        bauchbindeRunnable?.let { mainHandler.removeCallbacks(it) }

        if (track.isStationContent) {
            binding.tvBauchbindeBadge.text = "STATION ID // TV SIGNAL"
        } else {
            binding.tvBauchbindeBadge.text = "NOW PLAYING // STATION NORTH"
        }

        binding.tvBauchbindeTitle.text = track.title
        binding.tvBauchbindeArtist.text = track.artist

        binding.cardLowerThirds.visibility = View.VISIBLE
        val slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_right)
        binding.cardLowerThirds.startAnimation(slideIn)

        bauchbindeRunnable = Runnable {
            val fadeOut = AnimationUtils.loadAnimation(this@MainActivity, R.anim.fade_out)
            binding.cardLowerThirds.startAnimation(fadeOut)
            binding.cardLowerThirds.visibility = View.GONE
        }
        mainHandler.postDelayed(bauchbindeRunnable!!, 5000)
    }

    private fun setupArtistHistoryRecyclerView() {
        artistHistoryAdapter = ArtistHistoryAdapter(
            context = this,
            items = com.example.snfetchplayer.data.ArtistHistoryRepository.getHistory().toMutableList(),
            onItemClick = { track ->
                if (track.youtubeId != null) {
                    AppLogger.d("MainActivity", "User clicked history item: '${track.title}' [${track.youtubeId}]")
                    radioService?.playCuratorTrack(RawPlaylistItem(track.youtubeId, track.title, track.artist))
                }
            }
        )
        binding.rvArtistHistory.layoutManager = LinearLayoutManager(this)
        binding.rvArtistHistory.adapter = artistHistoryAdapter
    }

    private fun setupSettingsHub() {
        setupLexiconStudio()

        binding.toggleSettingsTabGroup.check(R.id.btnTabCurator)
        binding.layoutCuratorStudio.visibility = View.VISIBLE
        binding.layoutNordLogsStudio.visibility = View.GONE
        binding.layoutAboutShareStudio.visibility = View.GONE

        binding.toggleSettingsTabGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTabCurator -> {
                        binding.layoutCuratorStudio.visibility = View.VISIBLE
                        binding.layoutNordLogsStudio.visibility = View.GONE
                        binding.layoutAboutShareStudio.visibility = View.GONE
                    }
                    R.id.btnTabNordLogs -> {
                        binding.layoutCuratorStudio.visibility = View.GONE
                        binding.layoutNordLogsStudio.visibility = View.VISIBLE
                        binding.layoutAboutShareStudio.visibility = View.GONE
                        displayRandomLexiconArtist()
                    }
                    R.id.btnTabAboutShare -> {
                        binding.layoutCuratorStudio.visibility = View.GONE
                        binding.layoutNordLogsStudio.visibility = View.GONE
                        binding.layoutAboutShareStudio.visibility = View.VISIBLE
                    }
                }
            }
        }

        binding.btnShowNordLogs.setOnClickListener {
            if (binding.layoutNordLogsInline.visibility == View.VISIBLE) {
                binding.layoutNordLogsInline.visibility = View.GONE
                binding.btnShowNordLogs.text = "💻 Toggle Logs"
            } else {
                binding.layoutNordLogsInline.visibility = View.VISIBLE
                binding.btnShowNordLogs.text = "💻 Hide Logs"
                binding.tvNordLogsTerminal.text = AppLogger.getLogHistory().ifEmpty { "System Initialized. Awaiting YouTube stream pipeline..." }
            }
        }

        binding.btnCopyNordLogs.setOnClickListener {
            val logs = AppLogger.getLogHistory()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SN Terminal Logs", logs)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        binding.btnSaveNordLogsTxt.setOnClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            exportLogsTxtLauncher.launch("sn_terminal_logs_$timestamp.txt")
        }

        binding.btnSyncAssetsNow.setOnClickListener {
            com.example.snfetchplayer.manager.RemoteAssetSyncManager.startRemoteAssetSync(this) { progress ->
                updateAssetSyncUi(progress)
            }
        }

        binding.btnOpenEbookChronicles.setOnClickListener {
            ChroniclesEbookDialog(this).show()
        }
    }

    private fun updateAssetSyncUi(progress: com.example.snfetchplayer.manager.SyncProgress) {
        runOnUiThread {
            binding.pbRemoteAssetSync.progress = progress.progressPercent
            binding.tvAssetSyncStatus.text = progress.statusMessage
            if (progress.isSyncing) {
                binding.btnSyncAssetsNow.isEnabled = false
                binding.btnSyncAssetsNow.text = "⏳ Syncing..."
            } else {
                binding.btnSyncAssetsNow.isEnabled = true
                binding.btnSyncAssetsNow.text = "🔄 Sync Now"
            }
        }
    }

    private fun updateRadioWikipediaPanel(artistName: String, title: String, extract: String?, bitmap: android.graphics.Bitmap?, textColor: Int? = null) {
        binding.tvRadioArtistName.text = "$artistName • $title"
        if (!extract.isNullOrBlank()) {
            binding.tvRadioWikipediaSummary.text = extract
        } else {
            binding.tvRadioWikipediaSummary.text = "Station North Digital Radio Stream • Pure R&B & Urban Culture"
        }

        if (textColor != null) {
            binding.tvRadioWikipediaSummary.setTextColor(textColor)
        } else {
            binding.tvRadioWikipediaSummary.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.nord4))
        }

        if (bitmap != null) {
            binding.imgRadioArtistIcon.setImageBitmap(bitmap)
        } else {
            binding.imgRadioArtistIcon.setImageResource(R.drawable.sn_logo)
        }
    }

    private fun loadWikipediaArtistInfo(track: Track) {
        val lowerArtist = track.artist.lowercase()
        val isStation = track.isStationContent ||
                lowerArtist.contains("station north") ||
                lowerArtist.contains("station id") ||
                lowerArtist.contains("sn-tv")

        if (isStation) {
            val quote = com.example.snfetchplayer.manager.QueenQuotesManager.getRandomQuote()
            val quoteText = quote?.text ?: "The Queen is watching. Station North."
            val quoteColor = quote?.colorInt

            val stationItem = ArtistHistoryItem(
                track = track,
                displayTitle = "👑 THE QUEEN // STATION NORTH",
                imageUrl = null,
                imageBitmap = null,
                extract = quoteText,
                textColor = quoteColor
            )
            com.example.snfetchplayer.data.ArtistHistoryRepository.addOrUpdateItem(stationItem)
            artistHistoryAdapter.setItems(com.example.snfetchplayer.data.ArtistHistoryRepository.getHistory())
            binding.rvArtistHistory.scrollToPosition(0)
            updateRadioWikipediaPanel("👑 THE QUEEN", track.title, quoteText, null, quoteColor)
            return
        }

        val existingEntry = com.example.snfetchplayer.data.ArtistHistoryRepository.getHistory().firstOrNull { it.track.artist.equals(track.artist, ignoreCase = true) }
        if (existingEntry != null) {
            artistHistoryAdapter.setItems(com.example.snfetchplayer.data.ArtistHistoryRepository.getHistory())
            binding.rvArtistHistory.scrollToPosition(0)
            binding.progressArtistInfo.visibility = View.GONE
            updateRadioWikipediaPanel(existingEntry.track.artist, track.title, existingEntry.extract, existingEntry.imageBitmap)
            return
        }

        binding.progressArtistInfo.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.Main) {
            val info = com.example.snfetchplayer.data.WikipediaArtistFetcher.fetchArtistInfo(track.artist)
            binding.progressArtistInfo.visibility = View.GONE

            val displayTitle = info?.title ?: track.artist
            val extractText = if (info != null && info.extract.isNotBlank()) {
                info.extract
            } else {
                "Keine Wikipedia-Beschreibung für \"${track.artist}\" gefunden."
            }

            val historyItem = ArtistHistoryItem(
                track = track,
                displayTitle = displayTitle,
                imageUrl = info?.imageUrl,
                imageBitmap = info?.imageBitmap,
                extract = extractText
            )
            com.example.snfetchplayer.data.ArtistHistoryRepository.addOrUpdateItem(historyItem)
            artistHistoryAdapter.setItems(com.example.snfetchplayer.data.ArtistHistoryRepository.getHistory())
            binding.rvArtistHistory.scrollToPosition(0)
            updateRadioWikipediaPanel(track.artist, track.title, extractText, info?.imageBitmap)
        }
    }

    override fun onTrackChanged(track: Track?, mode: PlaybackMode) {
        runOnUiThread {
            if (track != null) {
                binding.tvTrackTitle.text = track.title
                binding.tvTrackArtist.text = track.artist
                binding.tvCuratorPreviewTitle.text = track.title
                binding.tvCuratorPreviewArtist.text = track.artist

                if (track.youtubeId != null) {
                    curatorAdapter.updatePlayingId(track.youtubeId)
                }

                updateRadioWikipediaPanel(track.artist, track.title, "Loading artist information...", null)
                triggerLowerThirdsBauchbinde(track)
                loadWikipediaArtistInfo(track)
            } else {
                binding.tvTrackTitle.text = getString(R.string.unknown_title)
                binding.tvTrackArtist.text = getString(R.string.unknown_artist)
                binding.tvCuratorPreviewTitle.text = "Select a track to play"
                binding.tvCuratorPreviewArtist.text = "SNFETCH PLAYER // CURATOR STUDIO"
                updateRadioWikipediaPanel("SN-RADIO", "LIVE AUDIO", "Station North Digital Radio Stream • Pure R&B & Urban Culture", null)
            }
        }
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        runOnUiThread {
            if (isPlaying) {
                binding.fabPlayPause.setIconResource(R.drawable.ic_pause)
                binding.hudPlayPause.setIconResource(R.drawable.ic_pause)
                binding.btnCuratorPlayPause.setIconResource(R.drawable.ic_pause)
            } else {
                binding.fabPlayPause.setIconResource(R.drawable.ic_play)
                binding.hudPlayPause.setIconResource(R.drawable.ic_play)
                binding.btnCuratorPlayPause.setIconResource(R.drawable.ic_play)
            }

            if (radioService?.currentMode == PlaybackMode.SN_RADIO) {
                binding.audioVisualizer.setAudioPlaying(isPlaying)
            }

            if (isBuffering) {
                binding.progressBarBuffering.visibility = View.VISIBLE
            } else {
                binding.progressBarBuffering.visibility = View.GONE
            }
        }
    }

    override fun onRepeatStateChanged(isRepeatOne: Boolean) {
        runOnUiThread {
            updateRepeatButtonState(isRepeatOne)
        }
    }

    private fun updateRepeatButtonState(isRepeatOne: Boolean) {
        if (isRepeatOne) {
            binding.fabRepeat.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord14))
            binding.fabRepeat.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord0))
            binding.fabRepeat.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord14))

            binding.hudRepeat.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord14))
            binding.hudRepeat.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord0))
            binding.hudRepeat.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord14))

            binding.btnCuratorRepeat.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord14))
            binding.btnCuratorRepeat.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord0))
            binding.btnCuratorRepeat.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord14))
        } else {
            binding.fabRepeat.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord13))
            binding.fabRepeat.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord0))
            binding.fabRepeat.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord13))

            binding.hudRepeat.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord13))
            binding.hudRepeat.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord0))
            binding.hudRepeat.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord13))

            binding.btnCuratorRepeat.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord13))
            binding.btnCuratorRepeat.iconTint = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord0))
            binding.btnCuratorRepeat.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.nord13))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupAudioSessionVisualizer()
            }
        }
    }

    private fun setupTvFocusEffects() {
        val nord8Color = ContextCompat.getColor(this, R.color.nord8)
        val nord0Color = ContextCompat.getColor(this, R.color.nord0)
        val nord6Color = ContextCompat.getColor(this, R.color.nord6)
        val density = resources.displayMetrics.density

        val toggleGroupButtons = listOf(
            binding.btnModeTv,
            binding.btnModeRadio,
            binding.btnModeSettings,
            binding.btnTabCurator,
            binding.btnTabNordLogs,
            binding.btnTabAboutShare,
            binding.btnTabImported,
            binding.btnTabSaved
        )

        val standaloneButtons = listOf(
            binding.btnFullscreenToggle,
            binding.fabPrevious,
            binding.fabPlayPause,
            binding.fabNext,
            binding.fabRepeat,
            binding.btnExitFullscreen,
            binding.hudPrevious,
            binding.hudPlayPause,
            binding.hudNext,
            binding.hudRepeat,
            binding.btnCuratorPlayPause,
            binding.btnCuratorPrev,
            binding.btnCuratorNext,
            binding.btnCuratorRepeat,
            binding.btnCuratorFullscreen,
            binding.etPlaylistInput,
            binding.btnLoadPlaylist,
            binding.btnExportJson,
            binding.btnClearCuratorList
        )

        // 1. Toggle Group Buttons: Field Illumination / Inward Glow (No scaling blowout)
        for (btn in toggleGroupButtons) {
            btn.isFocusable = true
            val origBgTint = btn.backgroundTintList
            val origTextColor = btn.textColors
            val origIconTint = btn.iconTint
            val origStrokeColor = btn.strokeColor
            val origStrokeWidth = btn.strokeWidth

            btn.setOnFocusChangeListener { v, hasFocus ->
                if (v is com.google.android.material.button.MaterialButton) {
                    if (hasFocus) {
                        v.backgroundTintList = ColorStateList.valueOf(nord8Color)
                        v.setTextColor(nord0Color)
                        v.iconTint = ColorStateList.valueOf(nord0Color)
                        v.strokeColor = ColorStateList.valueOf(nord6Color)
                        v.strokeWidth = (2.5f * density).toInt()
                    } else {
                        v.backgroundTintList = origBgTint
                        v.setTextColor(origTextColor)
                        v.iconTint = origIconTint
                        v.strokeColor = origStrokeColor
                        v.strokeWidth = origStrokeWidth
                    }
                }
            }
        }

        // 2. Standalone Action Tiles & HUD Controls: Inward Glow & Elevation
        for (view in standaloneButtons) {
            view.isFocusable = true
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.elevation = 8f * density
                    if (v is com.google.android.material.button.MaterialButton) {
                        v.strokeColor = ColorStateList.valueOf(nord8Color)
                        v.strokeWidth = (2.5f * density).toInt()
                    }

                    if (v == binding.btnExitFullscreen || v == binding.hudPrevious ||
                        v == binding.hudPlayPause || v == binding.hudNext || v == binding.hudRepeat) {
                        showHudTemporarily()
                    }
                } else {
                    v.elevation = 2f * density
                    if (v is com.google.android.material.button.MaterialButton) {
                        v.strokeWidth = (1.5f * density).toInt()
                    }
                }
            }
        }

        binding.fabPlayPause.post {
            binding.fabPlayPause.requestFocus()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isFullscreen) {
            showHudTemporarily()
            val isDpadKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_DPAD_UP ||
                    keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                    keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
                    keyCode == KeyEvent.KEYCODE_ENTER

            if (isDpadKey) {
                val currentFocused = currentFocus
                val isHudFocused = currentFocused == binding.btnExitFullscreen ||
                        currentFocused == binding.hudPrevious ||
                        currentFocused == binding.hudPlayPause ||
                        currentFocused == binding.hudNext ||
                        currentFocused == binding.hudRepeat

                if (!isHudFocused) {
                    binding.hudPlayPause.requestFocus()
                    return true
                }
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                radioService?.togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (radioService?.player?.isPlaying == false) {
                    radioService?.player?.play()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (radioService?.player?.isPlaying == true) {
                    radioService?.player?.pause()
                }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                radioService?.playNextTrack()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                radioService?.playPreviousTrack()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                radioService?.player?.pause()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (handleBackPressed()) {
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBackPressed(): Boolean {
        if (isFullscreen) {
            exitTrueFullscreen()
            return true
        }

        if (isSettingsModeActive) {
            isSettingsModeActive = false
            val activeMode = radioService?.currentMode ?: PlaybackMode.SN_TV
            binding.toggleModeGroup.check(if (activeMode == PlaybackMode.SN_RADIO) R.id.btnModeRadio else R.id.btnModeTv)
            updateUiMode(activeMode)
            return true
        }

        showTvExitConfirmationDialog()
        return true
    }

    private fun showTvExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit SNfetchPLAYER?")
            .setMessage("Would you like to completely exit the app and stop playback, or keep playing music in the background?")
            .setPositiveButton("Exit App & Service") { _, _ ->
                AppLogger.d("MainActivity", "User selected complete exit -> Stopping service & terminating task")
                radioService?.stopForegroundAndService()
                finishAndRemoveTask()
            }
            .setNeutralButton("Play in Background") { _, _ ->
                AppLogger.d("MainActivity", "User selected background playback -> Moving task to back")
                moveTaskToBack(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupLexiconStudio() {
        binding.rvSearchDropdown.layoutManager = LinearLayoutManager(this)
        lexiconSearchDropdownAdapter = LexiconSearchDropdownAdapter(emptyList()) { suggestion ->
            binding.cardSearchDropdown.visibility = View.GONE
            binding.etLexiconSearch.clearFocus()

            val artist = suggestion.artist
            renderLexiconArtistDetail(
                artist = artist,
                openAlbumTitle = suggestion.albumTitle,
                highlightTrackName = suggestion.trackName
            )
        }
        binding.rvSearchDropdown.adapter = lexiconSearchDropdownAdapter

        binding.etLexiconSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    binding.cardSearchDropdown.visibility = View.GONE
                } else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val suggestions = ArtistLexiconRepository.searchSuggestions(query, maxResults = 12)
                        withContext(Dispatchers.Main) {
                            if (suggestions.isNotEmpty() && binding.etLexiconSearch.hasFocus()) {
                                lexiconSearchDropdownAdapter.updateData(suggestions)
                                binding.cardSearchDropdown.visibility = View.VISIBLE
                            } else {
                                binding.cardSearchDropdown.visibility = View.GONE
                            }
                        }
                    }
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.etLexiconSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                binding.cardSearchDropdown.visibility = View.GONE
            } else {
                val query = binding.etLexiconSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val suggestions = ArtistLexiconRepository.searchSuggestions(query, maxResults = 12)
                        withContext(Dispatchers.Main) {
                            if (suggestions.isNotEmpty()) {
                                lexiconSearchDropdownAdapter.updateData(suggestions)
                                binding.cardSearchDropdown.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }

        binding.btnRandomArtist.setOnClickListener {
            displayRandomLexiconArtist()
        }
    }

    private fun displayRandomLexiconArtist() {
        lifecycleScope.launch(Dispatchers.IO) {
            ArtistLexiconRepository.ensureLoaded(this@MainActivity)
            val randomArtist = ArtistLexiconRepository.getRandomArtist()
            withContext(Dispatchers.Main) {
                randomArtist?.let { artist ->
                    renderLexiconArtistDetail(artist)
                }
            }
        }
    }

    private fun renderLexiconArtistDetail(
        artist: LexiconArtist,
        openAlbumTitle: String = "",
        highlightTrackName: String = ""
    ) {
        currentLexiconArtist = artist
        binding.cardSearchDropdown.visibility = View.GONE
        binding.etLexiconSearch.setText("")

        binding.tvArtistDetailName.text = artist.name
        binding.tvArtistDetailGenreBadge.text = when (artist.genre) {
            "US Hip-Hop" -> "🎤 US HIP-HOP"
            "R&B & Hip-Hop" -> "🔥 R&B & HIP-HOP"
            else -> "🎵 R&B / URBAN"
        }
        binding.imgArtistAvatar.setImageResource(R.drawable.sn_logo)
        binding.tvArtistDetailBio.text = artist.bio.ifEmpty { "No biography available in SN-Lexicon." }

        binding.btnArtistYoutube.setOnClickListener {
            openYoutubeSearch(artist.name)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val info = WikipediaArtistFetcher.fetchArtistInfo(artist.name)
            val bitmap = info?.imageBitmap
            withContext(Dispatchers.Main) {
                if (currentLexiconArtist?.id == artist.id && bitmap != null) {
                    binding.imgArtistAvatar.setImageBitmap(bitmap)
                }
            }
        }

        binding.layoutDiscographyContainer.removeAllViews()

        if (artist.discography.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = "No albums listed."
                setTextColor(ContextCompat.getColor(context, R.color.nord4))
                textSize = 11f
                setPadding(0, 8, 0, 8)
            }
            binding.layoutDiscographyContainer.addView(tvEmpty)
        } else {
            for (album in artist.discography) {
                val albumView = layoutInflater.inflate(R.layout.item_lexicon_album, binding.layoutDiscographyContainer, false)
                val tvAlbumYear = albumView.findViewById<TextView>(R.id.tvAlbumYear)
                val tvAlbumTitle = albumView.findViewById<TextView>(R.id.tvAlbumTitle)
                val tvAlbumTrackBadge = albumView.findViewById<TextView>(R.id.tvAlbumTrackBadge)
                val tvAlbumExpandIndicator = albumView.findViewById<TextView>(R.id.tvAlbumExpandIndicator)
                val layoutAlbumHeader = albumView.findViewById<View>(R.id.layoutAlbumHeader)
                val layoutTracklistContainer = albumView.findViewById<View>(R.id.layoutTracklistContainer)
                val layoutTrackItems = albumView.findViewById<LinearLayout>(R.id.layoutTrackItems)

                tvAlbumYear.text = album.year.ifEmpty { "N/A" }
                tvAlbumTitle.text = album.title
                tvAlbumTrackBadge.text = "${album.tracks.size} Songs"

                layoutTrackItems.removeAllViews()

                var containsHighlight = false

                for (track in album.tracks) {
                    val trackRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = android.view.Gravity.CENTER_VERTICAL
                        setPadding(0, 6, 0, 6)
                    }

                    val isHighlighted = highlightTrackName.isNotEmpty() && track.equals(highlightTrackName, ignoreCase = true)
                    if (isHighlighted) {
                        containsHighlight = true
                        trackRow.setBackgroundColor(ContextCompat.getColor(this, R.color.nord2))
                    }

                    val tvTrackName = TextView(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = track
                        textSize = 11.5f
                        setTextColor(ContextCompat.getColor(context, if (isHighlighted) R.color.nord13 else R.color.nord6))
                        if (isHighlighted) typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }

                    val btnYt = com.google.android.material.button.MaterialButton(
                        this,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle
                    ).apply {
                        text = "▶️ YT"
                        textSize = 9.5f
                        insetTop = 0
                        insetBottom = 0
                        minHeight = 0
                        minimumHeight = 0
                        setPadding(16, 4, 16, 4)
                        setTextColor(ContextCompat.getColor(context, R.color.nord11))
                        strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.nord11))
                        strokeWidth = 1
                        setOnClickListener {
                            openYoutubeSearch("${artist.name} - $track")
                        }
                    }

                    trackRow.addView(tvTrackName)
                    trackRow.addView(btnYt)
                    layoutTrackItems.addView(trackRow)
                }

                val shouldExpand = (openAlbumTitle.isNotEmpty() && album.title.equals(openAlbumTitle, ignoreCase = true)) || containsHighlight
                if (shouldExpand) {
                    layoutTracklistContainer.visibility = View.VISIBLE
                    tvAlbumExpandIndicator.text = "▲"
                } else {
                    layoutTracklistContainer.visibility = View.GONE
                    tvAlbumExpandIndicator.text = "▼"
                }

                layoutAlbumHeader.setOnClickListener {
                    if (layoutTracklistContainer.visibility == View.VISIBLE) {
                        layoutTracklistContainer.visibility = View.GONE
                        tvAlbumExpandIndicator.text = "▼"
                    } else {
                        layoutTracklistContainer.visibility = View.VISIBLE
                        tvAlbumExpandIndicator.text = "▲"
                    }
                }

                binding.layoutDiscographyContainer.addView(albumView)
            }
        }

        binding.layoutRelatedContainer.removeAllViews()

        if (artist.relatedArtists.isEmpty()) {
            binding.tvRelatedTitle.visibility = View.GONE
        } else {
            binding.tvRelatedTitle.visibility = View.VISIBLE
            for (rel in artist.relatedArtists) {
                val relCard = layoutInflater.inflate(R.layout.item_related_artist, binding.layoutRelatedContainer, false)
                val tvPlaceholder = relCard.findViewById<TextView>(R.id.tvRelatedAvatarPlaceholder)
                val tvName = relCard.findViewById<TextView>(R.id.tvRelatedName)

                tvPlaceholder.text = rel.name.take(1).uppercase()
                tvName.text = rel.name

                relCard.setOnClickListener {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val found = ArtistLexiconRepository.findArtistById(rel.id)
                            ?: ArtistLexiconRepository.findArtist(rel.name)
                        withContext(Dispatchers.Main) {
                            if (found != null) {
                                renderLexiconArtistDetail(found)
                            } else {
                                Toast.makeText(this@MainActivity, "No further details for ${rel.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                binding.layoutRelatedContainer.addView(relCard)
            }
        }

        binding.scrollArtistDetail.scrollTo(0, 0)
    }

    private fun openYoutubeSearch(query: String) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val ytUrl = "https://www.youtube.com/results?search_query=$encodedQuery"
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ytUrl))
            startActivity(intent)
        } catch (e: Exception) {
            AppLogger.e("MainActivity", "Failed to open YouTube link for query: $query", e)
            Toast.makeText(this, "Could not open web browser.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudioVisualizer()
        bauchbindeRunnable?.let { mainHandler.removeCallbacks(it) }
        hudTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        AppLogger.setLogListener(null)
        if (isBound) {
            radioService?.setServiceListener(null)
            unbindService(connection)
            isBound = false
        }
    }
}
