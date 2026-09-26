package com.pwde.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether the live session draws the profile's mapped buttons over the real game, and how strongly. */
data class ButtonOverlay(val shown: Boolean = false, val opacity: Float = DEFAULT_OPACITY) {
    companion object {
        const val DEFAULT_OPACITY = 0.6f
        const val MIN_OPACITY = 0.1f
    }
}

/**
 * The mapped-button overlay's settings: a debugging aid for checking that taps land on the game's
 * buttons. Kept in plain SharedPreferences, read synchronously by the accessibility service.
 */
class ButtonOverlayPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("button_overlay", Context.MODE_PRIVATE)
    private val _overlay = MutableStateFlow(
        ButtonOverlay(
            shown = prefs.getBoolean(KEY_SHOWN, false),
            opacity = prefs.getFloat(KEY_OPACITY, ButtonOverlay.DEFAULT_OPACITY),
        ),
    )
    val overlay: StateFlow<ButtonOverlay> = _overlay.asStateFlow()

    fun setShown(shown: Boolean) = save(_overlay.value.copy(shown = shown))

    fun setOpacity(opacity: Float) = save(_overlay.value.copy(opacity = opacity.coerceIn(ButtonOverlay.MIN_OPACITY, 1f)))

    private fun save(overlay: ButtonOverlay) {
        _overlay.value = overlay
        prefs.edit().putBoolean(KEY_SHOWN, overlay.shown).putFloat(KEY_OPACITY, overlay.opacity).apply()
    }

    private companion object {
        const val KEY_SHOWN = "shown"
        const val KEY_OPACITY = "opacity"
    }
}
