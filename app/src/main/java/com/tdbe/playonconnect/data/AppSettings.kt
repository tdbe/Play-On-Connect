package com.tdbe.playonconnect.data

import android.content.Context
import android.content.SharedPreferences

object AppSettings {

    private const val PREFS_UI = "ui_state"
    private const val KEY_PLAY_IN_PARALLEL = "play_in_parallel"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)
        }
    }

    /** When true (default), our playback neither steals audio focus nor gets stolen from. */
    var playInParallel: Boolean
        get() = prefs?.getBoolean(KEY_PLAY_IN_PARALLEL, true) ?: true
        set(value) {
            prefs?.edit()?.putBoolean(KEY_PLAY_IN_PARALLEL, value)?.apply()
        }
}
