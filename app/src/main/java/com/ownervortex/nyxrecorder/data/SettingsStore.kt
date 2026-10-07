package com.ownervortex.nyxrecorder.data

import android.content.Context
import android.content.SharedPreferences

object SettingsStore {
    private const val FILE = "nyx_settings"
    private const val KEY_QUALITY = "quality_preset"
    private const val KEY_FRAME_RATE = "frame_rate"
    private const val KEY_MIC = "record_mic"
    private const val KEY_DEVICE_AUDIO = "record_device_audio"
    private const val KEY_BUBBLE = "show_bubble"
    private const val KEY_BUBBLE_SIZE = "bubble_size"
    private const val KEY_BUBBLE_OPACITY = "bubble_opacity"
    private const val KEY_COUNTDOWN = "countdown"
    private const val KEY_FACE_CAM = "face_cam"
    private const val KEY_FACE_CAM_SIZE = "face_cam_size"
    private const val KEY_FACE_CAM_ROUND = "face_cam_round"
    private const val KEY_TOUCH = "touch_indicator"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val KEY_MUSIC_URI = "music_uri"
    private const val KEY_MUSIC_TITLE = "music_title"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_FIRST_RUN = "first_run"
    private const val KEY_THEME = "theme_index"
    private const val KEY_HOME_LABEL = "home_label"
    private const val KEY_SAVE_DIR = "save_dir"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    var qualityPreset: Int
        get() = prefs.getInt(KEY_QUALITY, 1)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    /** Target capture frame rate: one of 24, 30, 60. */
    var frameRate: Int
        get() = prefs.getInt(KEY_FRAME_RATE, 30).let { if (it in FPS_OPTIONS) it else 30 }
        set(value) = prefs.edit().putInt(KEY_FRAME_RATE, value.coerceIn(24, 60)).apply()

    val FPS_OPTIONS = intArrayOf(24, 30, 60)

    var recordMic: Boolean
        get() = prefs.getBoolean(KEY_MIC, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC, value).apply()

    var recordDeviceAudio: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVICE_AUDIO, value).apply()

    var showBubble: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE, value).apply()

    /** 0 = Small (48dp), 1 = Medium (64dp), 2 = Large (80dp). */
    var bubbleSize: Int
        get() = prefs.getInt(KEY_BUBBLE_SIZE, 1).coerceIn(0, 2)
        set(value) = prefs.edit().putInt(KEY_BUBBLE_SIZE, value.coerceIn(0, 2)).apply()

    /** On-screen dim opacity of the bubble while recording (0.10 … 1.0). */
    var bubbleOpacity: Float
        get() = prefs.getFloat(KEY_BUBBLE_OPACITY, 0.5f).coerceIn(0.1f, 1f)
        set(value) = prefs.edit().putFloat(KEY_BUBBLE_OPACITY, value.coerceIn(0.1f, 1f)).apply()

    var countdown: Boolean
        get() = prefs.getBoolean(KEY_COUNTDOWN, true)
        set(value) = prefs.edit().putBoolean(KEY_COUNTDOWN, value).apply()

    var faceCam: Boolean
        get() = prefs.getBoolean(KEY_FACE_CAM, false)
        set(value) = prefs.edit().putBoolean(KEY_FACE_CAM, value).apply()

    /** FaceCam size: 0 = Small (88dp), 1 = Medium (104dp), 2 = Large (120dp). */
    var faceCamSize: Int
        get() = prefs.getInt(KEY_FACE_CAM_SIZE, 1).coerceIn(0, 2)
        set(value) = prefs.edit().putInt(KEY_FACE_CAM_SIZE, value.coerceIn(0, 2)).apply()

    /** FaceCam shape: true = circle, false = rounded square. */
    var faceCamRound: Boolean
        get() = prefs.getBoolean(KEY_FACE_CAM_ROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_FACE_CAM_ROUND, value).apply()

    /** Custom home-screen watermark label; null/blank falls back to default. */
    var homeLabel: String?
        get() = prefs.getString(KEY_HOME_LABEL, null)
        set(value) = prefs.edit().putString(KEY_HOME_LABEL, value).apply()

    /**
     * Custom save folder, relative to the shared-storage root (e.g. "MyClips").
     * Null/blank falls back to the default "NYX Recorder" folder.
     */
    var saveDir: String?
        get() = prefs.getString(KEY_SAVE_DIR, null)
        set(value) = prefs.edit().putString(KEY_SAVE_DIR, value).apply()

    var touchIndicator: Boolean
        get() = prefs.getBoolean(KEY_TOUCH, false)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH, value).apply()

    var musicVolume: Float
        get() = prefs.getFloat(KEY_MUSIC_VOLUME, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_MUSIC_VOLUME, value).apply()

    var musicUri: String?
        get() = prefs.getString(KEY_MUSIC_URI, null)
        set(value) = prefs.edit().putString(KEY_MUSIC_URI, value).apply()

    var musicTitle: String?
        get() = prefs.getString(KEY_MUSIC_TITLE, null)
        set(value) = prefs.edit().putString(KEY_MUSIC_TITLE, value).apply()

    var firstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    /** Index into [com.ownervortex.nyxrecorder.ui.theme.THEME_NAMES]. */
    var themeIndex: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun setFavorite(id: String, favorite: Boolean) {
        val current = favorites().toMutableSet()
        if (favorite) current.add(id) else current.remove(id)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }
}
