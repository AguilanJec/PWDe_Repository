package com.pwde.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for app-wide settings. Exposed as a [Flow] so theme, text size and
 * layout changes propagate live to every screen.
 */
interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setAccessibilityNeeds(needs: Set<AccessibilityNeed>)
    suspend fun setAppearance(colorScheme: ColorSchemeOption, textSize: TextSizeOption, layoutMode: LayoutMode)
    suspend fun setInputMode(mode: InputMode)
    suspend fun setScreenReading(enabled: Boolean, speed: TtsSpeed, usesOtherScreenReader: Boolean)
    suspend fun setSetupCompleted(completed: Boolean)
    suspend fun setVoiceTutorialCompleted(completed: Boolean)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .map { it.toUserSettings() }
        .distinctUntilChanged()

    override suspend fun setAccessibilityNeeds(needs: Set<AccessibilityNeed>) {
        dataStore.edit { it[Keys.NEEDS] = needs.map { need -> need.name }.toSet() }
    }

    override suspend fun setAppearance(
        colorScheme: ColorSchemeOption,
        textSize: TextSizeOption,
        layoutMode: LayoutMode,
    ) {
        dataStore.edit {
            it[Keys.COLOR_SCHEME] = colorScheme.name
            it[Keys.TEXT_SIZE] = textSize.name
            it[Keys.LAYOUT_MODE] = layoutMode.name
        }
    }

    override suspend fun setInputMode(mode: InputMode) {
        dataStore.edit { it[Keys.INPUT_MODE] = mode.name }
    }

    override suspend fun setScreenReading(enabled: Boolean, speed: TtsSpeed, usesOtherScreenReader: Boolean) {
        dataStore.edit {
            it[Keys.TTS_ENABLED] = enabled
            it[Keys.TTS_SPEED] = speed.name
            it[Keys.OTHER_SCREEN_READER] = usesOtherScreenReader
        }
    }

    override suspend fun setSetupCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.SETUP_DONE] = completed }
    }

    override suspend fun setVoiceTutorialCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.TUTORIAL_DONE] = completed }
    }

    private object Keys {
        val NEEDS = stringSetPreferencesKey("accessibility_needs")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val TEXT_SIZE = stringPreferencesKey("text_size")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val INPUT_MODE = stringPreferencesKey("input_mode")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_SPEED = stringPreferencesKey("tts_speed")
        val OTHER_SCREEN_READER = booleanPreferencesKey("other_screen_reader")
        val SETUP_DONE = booleanPreferencesKey("setup_completed")
        val TUTORIAL_DONE = booleanPreferencesKey("voice_tutorial_completed")
    }

    private fun Preferences.toUserSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            accessibilityNeeds = this[Keys.NEEDS].orEmpty()
                .mapNotNull { enumOrNull<AccessibilityNeed>(it) }.toSet(),
            colorScheme = enumOrNull<ColorSchemeOption>(this[Keys.COLOR_SCHEME]) ?: defaults.colorScheme,
            textSize = enumOrNull<TextSizeOption>(this[Keys.TEXT_SIZE]) ?: defaults.textSize,
            layoutMode = enumOrNull<LayoutMode>(this[Keys.LAYOUT_MODE]) ?: defaults.layoutMode,
            inputMode = enumOrNull<InputMode>(this[Keys.INPUT_MODE]) ?: defaults.inputMode,
            ttsEnabled = this[Keys.TTS_ENABLED] ?: defaults.ttsEnabled,
            ttsSpeed = enumOrNull<TtsSpeed>(this[Keys.TTS_SPEED]) ?: defaults.ttsSpeed,
            usesOtherScreenReader = this[Keys.OTHER_SCREEN_READER] ?: defaults.usesOtherScreenReader,
            setupCompleted = this[Keys.SETUP_DONE] ?: defaults.setupCompleted,
            voiceTutorialCompleted = this[Keys.TUTORIAL_DONE] ?: defaults.voiceTutorialCompleted,
        )
    }
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
