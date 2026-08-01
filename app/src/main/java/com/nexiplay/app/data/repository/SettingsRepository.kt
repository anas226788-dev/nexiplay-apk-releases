package com.nexiplay.app.data.repository

import android.content.Context

class SettingsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    var sdCardUri: String?
        get() = prefs.getString("sd_card_uri", null)
        set(value) = prefs.edit().putString("sd_card_uri", value).apply()

    var isSdCardDownloadEnabled: Boolean
        get() = prefs.getBoolean("sd_card_download_enabled", false)
        set(value) = prefs.edit().putBoolean("sd_card_download_enabled", value).apply()

    var lastCommentTimeMs: Long
        get() = prefs.getLong("last_comment_time", 0L)
        set(value) = prefs.edit().putLong("last_comment_time", value).apply()

    var commentCountInWindow: Int
        get() = prefs.getInt("comment_count_in_window", 0)
        set(value) = prefs.edit().putInt("comment_count_in_window", value).apply()
}
