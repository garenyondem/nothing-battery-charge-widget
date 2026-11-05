package com.garen.glyphcharge

import android.content.Context

object ChargingPreferences {

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "meter_enabled"
    private const val KEY_ONLY_FACE_DOWN = "only_face_down"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_HEARTBEAT = "last_heartbeat"
    private const val KEY_LAST_CHARGING = "last_charging"
    private const val KEY_LAST_PERCENT = "last_percent"
    private const val KEY_FACE_RAW = "face_raw"
    private const val KEY_FACE_CONFIRMED = "face_confirmed"
    private const val KEY_FACE_SOURCE = "face_source"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isOnlyFaceDown(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ONLY_FACE_DOWN, true)

    fun setOnlyFaceDown(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ONLY_FACE_DOWN, enabled).apply()
    }

    fun themeMode(context: Context): Int =
        prefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)

    fun setThemeMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    fun heartbeat(context: Context): Long =
        prefs(context).getLong(KEY_HEARTBEAT, 0L)

    fun setHeartbeat(context: Context, value: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_HEARTBEAT, value).apply()
    }

    fun setLastDecision(context: Context, charging: Boolean, percent: Int) {
        prefs(context).edit()
            .putBoolean(KEY_LAST_CHARGING, charging)
            .putInt(KEY_LAST_PERCENT, percent)
            .apply()
    }

    fun lastCharging(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LAST_CHARGING, false)

    fun lastPercent(context: Context): Int =
        prefs(context).getInt(KEY_LAST_PERCENT, -1)

    fun setFaceState(context: Context, raw: Int, confirmed: Boolean, source: String) {
        prefs(context).edit()
            .putInt(KEY_FACE_RAW, raw)
            .putBoolean(KEY_FACE_CONFIRMED, confirmed)
            .putString(KEY_FACE_SOURCE, source)
            .apply()
    }

    fun faceRaw(context: Context): Int =
        prefs(context).getInt(KEY_FACE_RAW, -1)

    fun faceConfirmed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FACE_CONFIRMED, false)

    fun faceSource(context: Context): String? =
        prefs(context).getString(KEY_FACE_SOURCE, null)
}
