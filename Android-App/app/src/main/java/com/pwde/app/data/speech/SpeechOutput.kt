package com.pwde.app.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class SpeechStatus { NOT_STARTED, INITIALIZING, READY, UNAVAILABLE }

/**
 * Thin wrapper over Android [TextToSpeech] for the screen-reading option. The engine starts on
 * first use; text spoken before it is ready is held and played once it is.
 */
class SpeechOutput(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var pending: Pair<String, Float>? = null

    private val _status = MutableStateFlow(SpeechStatus.NOT_STARTED)
    val status: StateFlow<SpeechStatus> = _status.asStateFlow()

    fun speak(text: String, rate: Float) {
        if (text.isBlank()) return
        when (_status.value) {
            SpeechStatus.READY -> speakNow(text, rate)
            SpeechStatus.UNAVAILABLE -> Unit
            SpeechStatus.INITIALIZING -> pending = text to rate
            SpeechStatus.NOT_STARTED -> {
                pending = text to rate
                start()
            }
        }
    }

    fun stop() {
        pending = null
        tts?.stop()
    }

    private fun start() {
        _status.value = SpeechStatus.INITIALIZING
        tts = TextToSpeech(context.applicationContext) { result ->
            val engine = tts
            if (result != TextToSpeech.SUCCESS || engine == null) {
                _status.value = SpeechStatus.UNAVAILABLE
                return@TextToSpeech
            }
            val lang = engine.setLanguage(Locale.getDefault())
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.setLanguage(Locale.US)
            }
            _status.value = SpeechStatus.READY
            pending?.let { (text, rate) -> speakNow(text, rate) }
            pending = null
        }
    }

    private fun speakNow(text: String, rate: Float) {
        val engine = tts ?: return
        engine.setSpeechRate(rate)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pwde-${text.hashCode()}")
    }
}
