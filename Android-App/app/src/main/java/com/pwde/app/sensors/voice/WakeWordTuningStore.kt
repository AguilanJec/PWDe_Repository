package com.pwde.app.sensors.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The sherpa-onnx spotter tuning shared by every spotter in the app: the Testing Station's Wake word
 * panel edits it, and [SherpaInGameVoiceEngine] reads it, so numbers tuned there are the ones
 * gameplay presses buttons with.
 *
 * Phrases are keyed by [key], i.e. normalized the way gameplay normalizes its commands, so "Hey PWDE"
 * tuned in the Testing Station is the same entry as a "hey pwde" button trigger.
 *
 * In memory only: an app restart puts everything back on the defaults below.
 */
class WakeWordTuningStore {
    /** Per-phrase boost/threshold, keyed by [key]. A phrase missing from it uses [tuningFor]'s default. */
    private val _phraseTuning = MutableStateFlow<Map<String, WakeWordTuning>>(emptyMap())
    val phraseTuning: StateFlow<Map<String, WakeWordTuning>> = _phraseTuning.asStateFlow()

    /** The spotter-wide numbers, i.e. what a phrase tuned to [WakeWordTuning.INHERIT] uses. */
    private val _spotter = MutableStateFlow(WakeWordSpotterTuning())
    val spotter: StateFlow<WakeWordSpotterTuning> = _spotter.asStateFlow()

    /** Every phrase starts at the most eager preset, so a quiet or rushed attempt still lands. */
    fun tuningFor(phrase: String): WakeWordTuning = _phraseTuning.value[key(phrase)] ?: DEFAULT_PHRASE_TUNING

    /** [tuningFor] for each of [phrases] (keyed as given), in the form [WakeWordEngine.start] takes. */
    fun tuningFor(phrases: Collection<String>): Map<String, WakeWordTuning> = phrases.associateWith(::tuningFor)

    fun setPhraseTuning(phrase: String, tuning: WakeWordTuning) {
        _phraseTuning.update { it + (key(phrase) to tuning) }
    }

    fun setSpotter(tuning: WakeWordSpotterTuning) {
        _spotter.value = tuning
    }

    /** Every phrase back on the default preset, and the spotter on its defaults. */
    fun reset() {
        _phraseTuning.value = emptyMap()
        _spotter.value = WakeWordSpotterTuning()
    }

    companion object {
        val DEFAULT_PHRASE_TUNING: WakeWordTuning = WakeWordSensitivity.MAX.tuning

        fun key(phrase: String): String = CommandMatcher.normalize(phrase)
    }
}
