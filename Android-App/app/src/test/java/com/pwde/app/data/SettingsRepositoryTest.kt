package com.pwde.app.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.DataStoreSettingsRepository
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.data.prefs.TtsSpeed
import com.pwde.app.data.prefs.UserSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val testScope = TestScope(UnconfinedTestDispatcher())

    private fun repository() = DataStoreSettingsRepository(
        PreferenceDataStoreFactory.create(scope = testScope.backgroundScope) { tmp.root.resolve("settings.preferences_pb") },
    )

    @Test
    fun defaults_whenNothingSaved() = testScope.runTest {
        assertEquals(UserSettings(), repository().settings.first())
    }

    @Test
    fun savedValues_roundTrip() = testScope.runTest {
        val repo = repository()
        repo.setAccessibilityNeeds(setOf(AccessibilityNeed.MOVEMENT, AccessibilityNeed.SEEING))
        repo.setAppearance(ColorSchemeOption.CONTRAST, TextSizeOption.X_LARGE, LayoutMode.EASY_REACH)
        repo.setScreenReading(enabled = true, speed = TtsSpeed.SLOW, usesOtherScreenReader = false)
        repo.setSetupCompleted(true)

        val s = repo.settings.first()
        assertEquals(setOf(AccessibilityNeed.MOVEMENT, AccessibilityNeed.SEEING), s.accessibilityNeeds)
        assertEquals(ColorSchemeOption.CONTRAST, s.colorScheme)
        assertEquals(TextSizeOption.X_LARGE, s.textSize)
        assertEquals(LayoutMode.EASY_REACH, s.layoutMode)
        assertEquals(true, s.ttsEnabled)
        assertEquals(TtsSpeed.SLOW, s.ttsSpeed)
        assertEquals(true, s.setupCompleted)
    }
}
