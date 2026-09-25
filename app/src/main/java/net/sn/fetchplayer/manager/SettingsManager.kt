package net.sn.fetchplayer.manager

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "sn_settings_prefs"

    // Preference Keys
    private const val KEY_VIDEO_QUALITY = "video_quality" // "auto", "360", "480", "720", "1080", "2160"
    private const val KEY_BAUCHBINDEN_ENABLED = "bauchbinden_enabled"
    private const val KEY_OSD_TIMEOUT = "osd_timeout_seconds" // 3, 5, 10, -1 (always on)
    private const val KEY_FFT_VISUALIZER_ENABLED = "fft_visualizer_enabled"
    private const val KEY_BASS_PULSE_ENABLED = "bass_pulse_enabled"
    private const val KEY_QUEEN_QUOTES_ENABLED = "queen_quotes_enabled"
    private const val KEY_TV_CHANNEL_SOURCE = "tv_channel_source"
    private const val KEY_RADIO_CHANNEL_SOURCE = "radio_channel_source"
    private const val KEY_TV_CUSTOM_URL = "tv_custom_url"
    private const val KEY_RADIO_CUSTOM_URL = "radio_custom_url"

    // Extractor Source Toggle Keys
    private const val KEY_ENABLE_INNERTUBE = "enable_innertube"
    private const val KEY_ENABLE_STATION_NORTH = "enable_station_north"
    private const val KEY_ENABLE_YTDLP_API = "enable_ytdlp_api"
    private const val KEY_ENABLE_INVIDIOUS = "enable_invidious"
    private const val KEY_ENABLE_COBALT = "enable_cobalt"
    private const val KEY_ENABLE_PIPED = "enable_piped"

    // Custom Extractor URLs
    private const val KEY_YTDLP_API_URL = "custom_extractor_url" // Retain legacy key for smooth migration
    private const val KEY_INVIDIOUS_URL = "invidious_custom_url"
    private const val KEY_COBALT_URL = "cobalt_custom_url"
    private const val KEY_PIPED_URL = "piped_custom_url"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Video Quality
    fun getVideoQuality(context: Context): String {
        return getPrefs(context).getString(KEY_VIDEO_QUALITY, "auto") ?: "auto"
    }

    fun setVideoQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_VIDEO_QUALITY, quality).apply()
    }

    // OSD Bauchbinden
    fun isSlantedBauchbindenEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BAUCHBINDEN_ENABLED, true)
    }

    fun setSlantedBauchbindenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BAUCHBINDEN_ENABLED, enabled).apply()
    }

    // OSD Timeout
    fun getOsdTimeoutSeconds(context: Context): Int {
        return getPrefs(context).getInt(KEY_OSD_TIMEOUT, 5)
    }

    fun setOsdTimeoutSeconds(context: Context, timeout: Int) {
        getPrefs(context).edit().putInt(KEY_OSD_TIMEOUT, timeout).apply()
    }

    // Visualizer Settings
    fun isFftVisualizerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FFT_VISUALIZER_ENABLED, true)
    }

    fun setFftVisualizerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FFT_VISUALIZER_ENABLED, enabled).apply()
    }

    fun isBassPulseEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BASS_PULSE_ENABLED, true)
    }

    fun setBassPulseEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BASS_PULSE_ENABLED, enabled).apply()
    }

    fun isQueenQuotesEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_QUEEN_QUOTES_ENABLED, true)
    }

    fun setQueenQuotesEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_QUEEN_QUOTES_ENABLED, enabled).apply()
    }

    // Channel Routing
    fun getTvChannelSource(context: Context): String {
        return getPrefs(context).getString(KEY_TV_CHANNEL_SOURCE, "default_top50") ?: "default_top50"
    }

    fun setTvChannelSource(context: Context, source: String) {
        getPrefs(context).edit().putString(KEY_TV_CHANNEL_SOURCE, source).apply()
    }

    fun getRadioChannelSource(context: Context): String {
        return getPrefs(context).getString(KEY_RADIO_CHANNEL_SOURCE, "default_top50") ?: "default_top50"
    }

    fun setRadioChannelSource(context: Context, source: String) {
        getPrefs(context).edit().putString(KEY_RADIO_CHANNEL_SOURCE, source).apply()
    }

    fun getTvCustomUrl(context: Context): String {
        return getPrefs(context).getString(KEY_TV_CUSTOM_URL, "") ?: ""
    }

    fun setTvCustomUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_TV_CUSTOM_URL, url).apply()
    }

    fun getRadioCustomUrl(context: Context): String {
        return getPrefs(context).getString(KEY_RADIO_CUSTOM_URL, "") ?: ""
    }

    fun setRadioCustomUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_RADIO_CUSTOM_URL, url).apply()
    }

    // Playlist Channel Routing IDs
    fun getTvPlaylistId(context: Context): String {
        return getPrefs(context).getString("tv_playlist_id", "default_main_top100") ?: "default_main_top100"
    }

    fun setTvPlaylistId(context: Context, playlistId: String) {
        getPrefs(context).edit().putString("tv_playlist_id", playlistId).apply()
    }

    fun getRadioPlaylistId(context: Context): String {
        return getPrefs(context).getString("radio_playlist_id", "default_main_top100") ?: "default_main_top100"
    }

    fun setRadioPlaylistId(context: Context, playlistId: String) {
        getPrefs(context).edit().putString("radio_playlist_id", playlistId).apply()
    }

    // Extractor Toggles
    fun isInnerTubeEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ENABLE_INNERTUBE, true)
    fun setInnerTubeEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ENABLE_INNERTUBE, enabled).apply()

    fun isStationNorthEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ENABLE_STATION_NORTH, true)
    fun setStationNorthEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ENABLE_STATION_NORTH, enabled).apply()

    fun isYtdlpApiEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ENABLE_YTDLP_API, true)
    fun setYtdlpApiEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ENABLE_YTDLP_API, enabled).apply()

    fun isInvidiousEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ENABLE_INVIDIOUS, true)
    fun setInvidiousEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ENABLE_INVIDIOUS, enabled).apply()

    fun isCobaltEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ENABLE_COBALT, true)
    fun setCobaltEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ENABLE_COBALT, enabled).apply()

    fun isPipedEnabled(context: Context): Boolean = getPrefs(context).getBoolean(KEY_ENABLE_PIPED, true)
    fun setPipedEnabled(context: Context, enabled: Boolean) = getPrefs(context).edit().putBoolean(KEY_ENABLE_PIPED, enabled).apply()

    // Custom Extractor Instance URLs
    fun getYtdlpApiUrl(context: Context): String = getPrefs(context).getString(KEY_YTDLP_API_URL, "") ?: ""
    fun setYtdlpApiUrl(context: Context, url: String) = getPrefs(context).edit().putString(KEY_YTDLP_API_URL, url.trim()).apply()

    fun getInvidiousUrl(context: Context): String = getPrefs(context).getString(KEY_INVIDIOUS_URL, "") ?: ""
    fun setInvidiousUrl(context: Context, url: String) = getPrefs(context).edit().putString(KEY_INVIDIOUS_URL, url.trim()).apply()

    fun getCobaltUrl(context: Context): String = getPrefs(context).getString(KEY_COBALT_URL, "") ?: ""
    fun setCobaltUrl(context: Context, url: String) = getPrefs(context).edit().putString(KEY_COBALT_URL, url.trim()).apply()

    fun getPipedUrl(context: Context): String = getPrefs(context).getString(KEY_PIPED_URL, "") ?: ""
    fun setPipedUrl(context: Context, url: String) = getPrefs(context).edit().putString(KEY_PIPED_URL, url.trim()).apply()
}
