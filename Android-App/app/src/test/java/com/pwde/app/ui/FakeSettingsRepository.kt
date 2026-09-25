package com.pwde.app.ui

import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.data.prefs.TtsSpeed
import com.pwde.app.data.prefs.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setAccessibilityNeeds(needs: Set<AccessibilityNeed>) =
        settings.update { it.copy(accessibilityNeeds = needs) }

    override suspend fun setAppearance(colorScheme: ColorSchemeOption, textSize: TextSizeOption, layoutMode: LayoutMode) =
        settings.update { it.copy(colorScheme = colorScheme, textSize = textSize, layoutMode = layoutMode) }

    override suspend fun setInputMode(mode: InputMode) = settings.update { it.copy(inputMode = mode) }

    override suspend fun setPwdeEnabled(enabled: Boolean) = settings.update { it.copy(pwdeEnabled = enabled) }

    override suspend fun setScreenReading(enabled: Boolean, speed: TtsSpeed, usesOtherScreenReader: Boolean) =
        settings.update { it.copy(ttsEnabled = enabled, ttsSpeed = speed, usesOtherScreenReader = usesOtherScreenReader) }

    override suspend fun setSetupCompleted(completed: Boolean) = settings.update { it.copy(setupCompleted = completed) }

    override suspend fun setVoiceTutorialCompleted(completed: Boolean) =
        settings.update { it.copy(voiceTutorialCompleted = completed) }
}
