package com.pwde.app.sensors.eyedid

import android.content.Context

/**
 * Remembers the calibration blob the SDK hands back from `onCalibrationFinished`, so the user
 * calibrates once instead of on every visit.
 *
 * The blob is opaque — only the SDK can interpret it — so it is stored verbatim and never inspected.
 * It is device- and user-specific (it encodes where *your* eyes sit relative to *this* screen), which
 * is also why it is not synced anywhere.
 */
class EyedidCalibrationStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The stored blob, or an empty list when there is none or it did not parse. */
    fun load(): List<Double> = prefs.getString(KEY_BLOB, null)
        ?.split(',')
        ?.mapNotNull { it.toDoubleOrNull() }
        .orEmpty()

    fun save(data: List<Double>) {
        if (data.isEmpty()) return
        prefs.edit().putString(KEY_BLOB, data.joinToString(",")).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_BLOB).apply()
    }

    private companion object {
        const val PREFS = "eyedid_calibration"
        const val KEY_BLOB = "blob"
    }
}
