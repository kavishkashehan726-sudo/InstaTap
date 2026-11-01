package com.shehan.instatap

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around the app's only persisted data: user settings and overlay positions. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("instatap", Context.MODE_PRIVATE)

    /** Extra delay after each tap completes before the next one starts. 0 = as fast as possible. */
    var intervalMs: Long
        get() = sp.getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS)
        set(v) = sp.edit().putLong(KEY_INTERVAL, v.coerceIn(0, MAX_INTERVAL_MS)).apply()

    /** Minutes after Start before tapping stops on its own. 0 = never. */
    var autoStopMinutes: Int
        get() = sp.getInt(KEY_AUTO_STOP, DEFAULT_AUTO_STOP_MIN)
        set(v) = sp.edit().putInt(KEY_AUTO_STOP, v.coerceIn(0, MAX_AUTO_STOP_MIN)).apply()

    fun targetPos(defX: Int, defY: Int) = sp.getInt(KEY_TX, defX) to sp.getInt(KEY_TY, defY)
    fun saveTargetPos(x: Int, y: Int) = sp.edit().putInt(KEY_TX, x).putInt(KEY_TY, y).apply()

    fun panelPos(defX: Int, defY: Int) = sp.getInt(KEY_PX, defX) to sp.getInt(KEY_PY, defY)
    fun savePanelPos(x: Int, y: Int) = sp.edit().putInt(KEY_PX, x).putInt(KEY_PY, y).apply()

    companion object {
        const val DEFAULT_INTERVAL_MS = 0L
        const val MAX_INTERVAL_MS = 60_000L
        const val DEFAULT_AUTO_STOP_MIN = 60
        const val MAX_AUTO_STOP_MIN = 24 * 60

        private const val KEY_INTERVAL = "interval_ms"
        private const val KEY_AUTO_STOP = "auto_stop_min"
        private const val KEY_TX = "target_x"
        private const val KEY_TY = "target_y"
        private const val KEY_PX = "panel_x"
        private const val KEY_PY = "panel_y"
    }
}
