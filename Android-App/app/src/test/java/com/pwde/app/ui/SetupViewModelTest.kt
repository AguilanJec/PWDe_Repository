package com.pwde.app.ui

import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.ui.setup.SetupStep
import com.pwde.app.ui.setup.SetupViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SetupViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()

    @Test
    fun continue_persistsEachStep_andFinishMarksSetupComplete() {
        val vm = SetupViewModel(settings, appearanceOnly = false)

        vm.toggleNeed(AccessibilityNeed.MOVEMENT)
        vm.continueStep()
        assertEquals(setOf(AccessibilityNeed.MOVEMENT), settings.settings.value.accessibilityNeeds)
        assertEquals(SetupStep.APPEARANCE, vm.state.value.step)

        vm.setColorScheme(ColorSchemeOption.LIGHT)
        vm.setTextSize(TextSizeOption.LARGE)
        vm.setLayoutMode(LayoutMode.EASY_REACH)
        vm.continueStep()
        with(settings.settings.value) {
            assertEquals(ColorSchemeOption.LIGHT, colorScheme)
            assertEquals(TextSizeOption.LARGE, textSize)
            assertEquals(LayoutMode.EASY_REACH, layoutMode)
        }

        vm.setInputMode(InputMode.VOICE)
        vm.continueStep()
        assertEquals(InputMode.VOICE, settings.settings.value.inputMode)
        assertTrue(settings.settings.value.setupCompleted)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun skip_doesNotPersist_andResetsTheDraftPreview() {
        val vm = SetupViewModel(settings, appearanceOnly = false)
        vm.continueStep() // needs → appearance

        vm.setColorScheme(ColorSchemeOption.CONTRAST)
        vm.skipStep()

        assertEquals(ColorSchemeOption.DEFAULT, settings.settings.value.colorScheme)
        assertEquals(ColorSchemeOption.DEFAULT, vm.state.value.colorScheme)
        assertEquals(SetupStep.INPUT, vm.state.value.step)
    }

    @Test
    fun appearanceOnly_hasOneStep_andDoesNotTouchSetupCompleted() {
        val vm = SetupViewModel(settings, appearanceOnly = true)
        assertEquals(listOf(SetupStep.APPEARANCE), vm.state.value.steps)

        vm.setTextSize(TextSizeOption.X_LARGE)
        vm.continueStep()

        assertEquals(TextSizeOption.X_LARGE, settings.settings.value.textSize)
        assertFalse(settings.settings.value.setupCompleted)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun back_onFirstStep_returnsFalse() {
        val vm = SetupViewModel(settings, appearanceOnly = false)
        assertFalse(vm.back())
        vm.continueStep()
        assertTrue(vm.back())
        assertEquals(SetupStep.NEEDS, vm.state.value.step)
    }
}
