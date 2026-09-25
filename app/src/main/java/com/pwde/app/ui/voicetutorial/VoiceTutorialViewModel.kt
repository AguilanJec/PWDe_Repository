package com.pwde.app.ui.voicetutorial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.prefs.TtsSpeed
import com.pwde.app.data.speech.SpeechOutput
import com.pwde.app.data.speech.SpeechStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VoiceTutorialUiState(
    val step: Int = 0,
    val ttsEnabled: Boolean = false,
    val ttsSpeed: TtsSpeed = TtsSpeed.NORMAL,
    val usesOtherScreenReader: Boolean = false,
    val systemScreenReaderOn: Boolean = false,
    val speechStatus: SpeechStatus = SpeechStatus.NOT_STARTED,
    val finished: Boolean = false,
) {
    val isLastStep: Boolean get() = step == STEP_COUNT - 1

    companion object {
        const val STEP_COUNT = 3
    }
}

class VoiceTutorialViewModel(
    private val settingsRepository: SettingsRepository,
    private val speechOutput: SpeechOutput,
    systemScreenReaderOn: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(VoiceTutorialUiState(systemScreenReaderOn = systemScreenReaderOn))
    val state: StateFlow<VoiceTutorialUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            _state.update {
                it.copy(
                    ttsEnabled = saved.ttsEnabled,
                    ttsSpeed = saved.ttsSpeed,
                    // If TalkBack is already on, default to not doubling up on speech.
                    usesOtherScreenReader = saved.usesOtherScreenReader || systemScreenReaderOn,
                )
            }
        }
        viewModelScope.launch { speechOutput.status.collect { s -> _state.update { it.copy(speechStatus = s) } } }
    }

    fun setTtsEnabled(enabled: Boolean) = _state.update {
        it.copy(ttsEnabled = enabled, usesOtherScreenReader = if (enabled) false else it.usesOtherScreenReader)
    }

    fun setSpeed(speed: TtsSpeed) = _state.update { it.copy(ttsSpeed = speed) }

    fun setUsesOtherScreenReader(value: Boolean) = _state.update {
        it.copy(usesOtherScreenReader = value, ttsEnabled = if (value) false else it.ttsEnabled)
    }

    fun preview() {
        speechOutput.speak(PREVIEW_TEXT, _state.value.ttsSpeed.rate)
    }

    /** Continue: the last step saves the read-aloud choice. */
    fun next() {
        val s = _state.value
        if (!s.isLastStep) return _state.update { it.copy(step = it.step + 1) }
        viewModelScope.launch {
            settingsRepository.setScreenReading(s.ttsEnabled, s.ttsSpeed, s.usesOtherScreenReader)
            finish()
        }
    }

    /** Skip: moves on; on the last step, leaves read-aloud settings as they were. */
    fun skip() {
        val s = _state.value
        if (!s.isLastStep) return _state.update { it.copy(step = it.step + 1) }
        viewModelScope.launch { finish() }
    }

    fun back(): Boolean {
        if (_state.value.step == 0) return false
        _state.update { it.copy(step = it.step - 1) }
        return true
    }

    private suspend fun finish() {
        speechOutput.stop()
        settingsRepository.setVoiceTutorialCompleted(true)
        _state.update { it.copy(finished = true) }
    }

    override fun onCleared() {
        speechOutput.stop()
    }

    companion object {
        const val PREVIEW_TEXT = "Hi! This is how PWDe will read the screen to you. Say a button's name to press it."
    }
}
