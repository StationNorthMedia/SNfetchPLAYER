package net.sn.fetchplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import net.sn.fetchplayer.R
import net.sn.fetchplayer.manager.PlaylistManager
import net.sn.fetchplayer.manager.StationContentManager
import net.sn.fetchplayer.model.PlaybackMode
import net.sn.fetchplayer.model.Track
import net.sn.fetchplayer.ui.MainActivity
import net.sn.fetchplayer.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioService : Service() {

    companion object {
        const val CHANNEL_ID = "sn_radio_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "action_play"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_NEXT = "action_next"
        const val ACTION_PREVIOUS = "action_previous"
        const val ACTION_STOP = "action_stop"
    }

    private val binder = RadioBinder()
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    lateinit var player: ExoPlayer
        private set

    lateinit var playlistManager: PlaylistManager
        private set

    lateinit var stationContentManager: StationContentManager
        private set

    var currentMode: PlaybackMode = PlaybackMode.SN_TV
        private set

    var currentTrack: Track? = null
        private set

    var isRepeatOneActive: Boolean = false
        private set

    private var listener: ServiceListener? = null
    private var prepareJob: Job? = null

    interface ServiceListener {
        fun onTrackChanged(track: Track?, mode: PlaybackMode)
        fun onPlaybackStateChanged(isPlaying: Boolean, isBuffering: Boolean)
        fun onRepeatStateChanged(isRepeatOne: Boolean)
    }

    inner class RadioBinder : Binder() {
        fun getService(): RadioService = this@RadioService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        stationContentManager = StationContentManager(this)
        playlistManager = PlaylistManager(this, stationContentManager)

        val headers = mapOf(
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/"
        )

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip")
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(defaultDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.repeatMode = Player.REPEAT_MODE_OFF

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.localConfiguration?.tag?.let { tag ->
                    if (tag is Track) {
                        currentTrack = tag
                        AppLogger.d("RadioService", "Track Transition -> '${tag.artist} - ${tag.title}' [Mode: $currentMode]")
                        listener?.onTrackChanged(tag, currentMode)
                        updateNotification()
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                AppLogger.d("RadioService", "ExoPlayer State -> $stateName (IsPlaying=${player.isPlaying})")
                listener?.onPlaybackStateChanged(player.isPlaying, isBuffering)

                if (playbackState == Player.STATE_ENDED) {
                    if (isRepeatOneActive) {
                        AppLogger.d("RadioService", "Repeat One active -> Replaying current song")
                        player.seekTo(0)
                        player.play()
                    } else {
                        playNextTrack()
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                AppLogger.e("RadioService", "ExoPlayer Exception [Code: ${error.errorCodeName}] message: ${error.message}", error)
                playNextTrack()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                AppLogger.d("RadioService", "IsPlayingChanged -> $isPlaying")
                listener?.onPlaybackStateChanged(isPlaying, player.playbackState == Player.STATE_BUFFERING)
                updateNotification()
            }
        })

        // Initial track load
        serviceScope.launch {
            playNextTrack()
        }

        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_NEXT -> playNextTrack()
            ACTION_PREVIOUS -> playPreviousTrack()
            ACTION_STOP -> stopForegroundAndService()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setServiceListener(serviceListener: ServiceListener?) {
        this.listener = serviceListener
        listener?.onTrackChanged(currentTrack, currentMode)
        listener?.onPlaybackStateChanged(player.isPlaying, player.playbackState == Player.STATE_BUFFERING)
        listener?.onRepeatStateChanged(isRepeatOneActive)
    }

    fun setPlaybackMode(newMode: PlaybackMode) {
        if (currentMode == newMode) return
        currentMode = newMode
        AppLogger.d("RadioService", "Switched playback mode to: $newMode")

        player.clearMediaItems()
        playNextTrack()
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun toggleRepeatOne() {
        isRepeatOneActive = !isRepeatOneActive
        player.repeatMode = if (isRepeatOneActive) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        AppLogger.d("RadioService", "Repeat One toggled -> Active: $isRepeatOneActive")
        listener?.onRepeatStateChanged(isRepeatOneActive)
    }

    fun playPreviousTrack() {
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }

        prepareJob?.cancel()
        prepareJob = serviceScope.launch {
            val prevTrack = playlistManager.getPreviousTrack()
            if (prevTrack != null) {
                loadAndPlayTrack(prevTrack)
            } else {
                player.seekTo(0)
            }
        }
    }

    fun playNextTrack() {
        prepareJob?.cancel()
        prepareJob = serviceScope.launch {
            AppLogger.d("RadioService", "playNextTrack requested immediately")
            val nextTrack = playlistManager.getNextTrack(currentMode)
            loadAndPlayTrack(nextTrack)
        }
    }

    fun playCuratorTrack(rawItem: PlaylistManager.RawPlaylistItem) {
        prepareJob?.cancel()
        prepareJob = serviceScope.launch {
            AppLogger.d("RadioService", "playCuratorTrack requested: '${rawItem.artist} - ${rawItem.title}' [${rawItem.youtubeId}]")
            val track = playlistManager.resolveTrack(rawItem, currentMode)
            if (track != null) {
                loadAndPlayTrack(track)
            } else {
                AppLogger.e("RadioService", "Failed to resolve stream for curator track [${rawItem.youtubeId}]")
            }
        }
    }

    private suspend fun loadAndPlayTrack(track: Track) {
        val streamUrl = track.resolvedStreamUrl
        if (!streamUrl.isNullOrEmpty()) {
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(streamUrl))
                .setMediaMetadata(mediaMetadata)
                .setTag(track)
                .build()

            withContext(Dispatchers.Main) {
                player.stop()
                player.clearMediaItems()
                player.setMediaItem(mediaItem)
                player.prepare()
                player.playWhenReady = true
            }
        } else {
            AppLogger.e("RadioService", "Cannot play track [${track.title}], stream URL is null or empty")
        }

        playlistManager.prefetchNextTracks(currentMode, 2)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (player.isPlaying) {
            val pauseIntent = Intent(this, RadioService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.ic_pause, getString(R.string.pause), pausePending)
        } else {
            val playIntent = Intent(this, RadioService::class.java).apply { action = ACTION_PLAY }
            val playPending = PendingIntent.getService(this, 2, playIntent, PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action(R.drawable.ic_play, getString(R.string.play), playPending)
        }

        val nextIntent = Intent(this, RadioService::class.java).apply { action = ACTION_NEXT }
        val nextPending = PendingIntent.getService(this, 3, nextIntent, PendingIntent.FLAG_IMMUTABLE)
        val nextAction = NotificationCompat.Action(R.drawable.ic_next, getString(R.string.next), nextPending)

        val modeTitle = if (currentMode == PlaybackMode.SN_TV) "SN-TV" else "SN-RADIO"
        val title = currentTrack?.title ?: getString(R.string.station_north)
        val artist = currentTrack?.artist ?: modeTitle

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$modeTitle: $title")
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setOngoing(true)
            .setStyle(MediaNotificationCompat.MediaStyle().setShowActionsInCompactView(0, 1))
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    fun stopForegroundAndService() {
        AppLogger.d("RadioService", "stopForegroundAndService called -> Stopping playback and service")
        try {
            player.stop()
            player.clearMediaItems()
        } catch (e: Exception) {
            AppLogger.e("RadioService", "Error stopping player during service shutdown", e)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        player.release()
        super.onDestroy()
    }
}
