package com.pwde.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.prefs.UserSettings
import com.pwde.app.data.speech.SpeechOutput
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Announces each screen's name when the user turned on PWDe's read-aloud (and not another reader). */
class ScreenReaderViewModel(
    settingsRepository: SettingsRepository,
    private val speechOutput: SpeechOutput,
) : ViewModel() {
    private val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onScreenShown(title: String?) {
        val s = settings.value ?: return
        if (title != null && s.ttsEnabled && !s.usesOtherScreenReader) {
            speechOutput.speak(title, s.ttsSpeed.rate)
        }
    }
}
