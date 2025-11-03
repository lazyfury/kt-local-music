package com.example.myapplication

import android.content.Context

/**
 * 播放相关设置持久化：当前仅包含“是否启用随机播放”。
 */
object PlaybackSettings {
    private const val PREFS_NAME = "playback_settings"
    private const val KEY_SHUFFLE_ENABLED = "shuffle_enabled"

    fun isShuffleEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
    }

    fun setShuffleEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHUFFLE_ENABLED, enabled).apply()
    }
}