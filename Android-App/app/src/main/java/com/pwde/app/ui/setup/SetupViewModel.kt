package com.pwde.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.prefs.TextSizeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep(val label: String) {
    APPEARANCE("How it looks"),
    NEEDS("What you need"),
    PERMISSIONS("Permissions"),
}

data class SetupUiState(
    val loaded: Boolean = false,
    val steps: List<SetupStep> = SetupStep.entries,
    val stepIndex: Int = 0,
    val needs: Set<AccessibilityNeed> = emptySet(),
    val colorScheme: ColorSchemeOption = ColorSchemeOption.DEFAULT,
    val textSize: TextSizeOption = TextSizeOption.MEDIUM,
    val layoutMode: LayoutMode = LayoutMode.STANDARD,
    val finished: Boolean = false,
) {
    val step: SetupStep get() = steps[stepIndex]
    val isLastStep: Boolean get() = stepIndex == steps.lastIndex
}

/**
 * Skippable steps. Edits are held as a draft (so the Setup screen can preview them live)
 * and written to [SettingsRepository] only when the user taps Continue on that step.
 * The permissions (B5) step grants things in Android itself (camera, mic, accessibility service,
 * display-over-apps), so it saves nothing.
 *
 * @param appearanceOnly opened from Profile to change just the look; finishing returns there.
 */
class SetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val appearanceOnly: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SetupUiState(steps = if (appearanceOnly) listOf(SetupStep.APPEARANCE) else SetupStep.entries),
    )
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            _state.update {
                it.copy(
                    loaded = true,
                    needs = saved.accessibilityNeeds,
                    colorScheme = saved.colorScheme,
                    textSize = saved.textSize,
                    layoutMode = saved.layoutMode,
                )
            }
        }
    }

    fun toggleNeed(need: AccessibilityNeed) = _state.update {
        it.copy(needs = if (need in it.needs) it.needs - need else it.needs + need)
    }

    fun setColorScheme(option: ColorSchemeOption) = _state.update { it.copy(colorScheme = option) }

    fun setTextSize(option: TextSizeOption) = _state.update { it.copy(textSize = option) }

    fun setLayoutMode(mode: LayoutMode) = _state.update { it.copy(layoutMode = mode) }

    /** Saves this step, then moves on. */
    fun continueStep() {
        val s = _state.value
        viewModelScope.launch {
            when (s.step) {
                SetupStep.APPEARANCE -> settingsRepository.setAppearance(s.colorScheme, s.textSize, s.layoutMode)
                SetupStep.NEEDS -> settingsRepository.setAccessibilityNeeds(s.needs)
                SetupStep.PERMISSIONS -> Unit
            }
            advance()
        }
    }

    /** Moves on without saving; this step's draft is reset to what is saved. */
    fun skipStep() {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            _state.update {
                when (it.step) {
                    SetupStep.APPEARANCE -> it.copy(
                        colorScheme = saved.colorScheme,
                        textSize = saved.textSize,
                        layoutMode = saved.layoutMode,
                    )
                    SetupStep.NEEDS -> it.copy(needs = saved.accessibilityNeeds)
                    SetupStep.PERMISSIONS -> it
                }
            }
            advance()
        }
    }

    /** @return false when already on the first step (caller should leave the screen). */
    fun back(): Boolean {
        if (_state.value.stepIndex == 0) return false
        _state.update { it.copy(stepIndex = it.stepIndex - 1) }
        return true
    }

    private suspend fun advance() {
        val s = _state.value
        if (s.isLastStep) {
            if (!appearanceOnly) settingsRepository.setSetupCompleted(true)
            _state.update { it.copy(finished = true) }
        } else {
            _state.update { it.copy(stepIndex = it.stepIndex + 1) }
        }
    }
}
