package com.ownervortex.nyxrecorder.data

import android.content.Context
import android.content.SharedPreferences

object SettingsStore {
    private const val FILE = "nyx_settings"
    private const val KEY_QUALITY = "quality_preset"
    private const val KEY_MIC = "record_mic"
    private const val KEY_DEVICE_AUDIO = "record_device_audio"
    private const val KEY_BUBBLE = "show_bubble"
    private const val KEY_COUNTDOWN = "countdown"
    private const val KEY_FACE_CAM = "face_cam"
    private const val KEY_TOUCH = "touch_indicator"
    private const val KEY_MUSIC_VOLUME = "music_volume"
    private const val KEY_MUSIC_URI = "music_uri"
    private const val KEY_MUSIC_TITLE = "music_title"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_FIRST_RUN = "first_run"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    var qualityPreset: Int
        get() = prefs.getInt(KEY_QUALITY, 1)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    var recordMic: Boolean
        get() = prefs.getBoolean(KEY_MIC, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC, value).apply()

    var recordDeviceAudio: Boolean
        get() = prefs.getBoolean(KEY_DEVICE_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEVICE_AUDIO, value).apply()

    var showBubble: Boolean
        get() = prefs.getBoolean(KEY_BUBBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_BUBBLE, value).apply()

    var countdown: Boolean
        get() = prefs.getBoolean(KEY_COUNTDOWN, true)
        set(value) = prefs.edit().putBoolean(KEY_COUNTDOWN, value).apply()

    var faceCam: Boolean
        get() = prefs.getBoolean(KEY_FACE_CAM, false)
        set(value) = prefs.edit().putBoolean(KEY_FACE_CAM, value).apply()

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

    fun favorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun setFavorite(id: String, favorite: Boolean) {
        val current = favorites().toMutableSet()
        if (favorite) current.add(id) else current.remove(id)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }
}
