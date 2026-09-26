package com.pwde.app.ui.setup

import androidx.lifecycle.viewModelScope
import com.pwde.app.data.gabai.Axis
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep(val label: String) {

    TURN_ON("Turn on PWDe"),
    PERMISSIONS("Permissions"),
    CURSOR_CALIBRATION("Cursor calibration"),
    NEEDS("What you need"),
    APPEARANCE("How it looks"),

}

data class SetupUiState(
    val loaded: Boolean = false,
    val steps: List<SetupStep> = SetupStep.entries,
    val stepIndex: Int = 0,
    val needs: Set<AccessibilityNeed> = emptySet(),
    val colorScheme: ColorSchemeOption = ColorSchemeOption.DEFAULT,
    val textSize: TextSizeOption = TextSizeOption.MEDIUM,
    val layoutMode: LayoutMode = LayoutMode.STANDARD,
    /** Which direction the cursor calibration step (B7) is currently on. */
    val axis: Axis = Axis.entries.first(),
    val cursor: CursorTuning = CursorTuning(),
    val finished: Boolean = false,
) {
    val step: SetupStep get() = steps[stepIndex]
    val isLastStep: Boolean get() = stepIndex == steps.lastIndex
}

/**
 * Skippable steps. Edits are held as a draft (so the Setup screen can preview them live)
 * and written to [SettingsRepository] only when the user taps Continue on that step.
 * The permission (B5) and Settings (B6) steps grant things in Android itself, so they save nothing.
 * The cursor calibration step (B7) is GabAI's own axis-by-axis walkthrough, reused here; like GabAI
 * it writes each adjustment straight to [ControlsRepository] as it's made, so there is nothing to
 * commit on Continue either.
 *
 * @param appearanceOnly opened from Profile to change just the look; finishing returns there.
 */
class SetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val controlsRepository: ControlsRepository,
    private val appearanceOnly: Boolean,
    faceTracking: FaceTrackingManager,
) : FaceTrackingViewModel(faceTracking) {
    private val _state = MutableStateFlow(
        SetupUiState(steps = if (appearanceOnly) listOf(SetupStep.APPEARANCE) else SetupStep.entries),
    )
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            val cursor = controlsRepository.config.first().cursor
            _state.update {
                it.copy(
                    loaded = true,
                    needs = saved.accessibilityNeeds,
                    colorScheme = saved.colorScheme,
                    textSize = saved.textSize,
                    layoutMode = saved.layoutMode,
                    cursor = cursor,
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

    /** Applies one axis's speed/smoothing, live, exactly as GabAI's own calibration does. */
    fun setCursor(tuning: CursorTuning) {
        _state.update { it.copy(cursor = tuning) }
        viewModelScope.launch { controlsRepository.setCursorTuning(tuning) }
    }

    /** Moves to the next direction; on the last one, finishes the step like Continue would. */
    fun axisDone() {
        val next = _state.value.axis.next()
        if (next != null) {
            _state.update { it.copy(axis = next) }
        } else {
            continueStep()
        }
    }

    /** Saves this step, then moves on. */
    fun continueStep() {
        val s = _state.value
        viewModelScope.launch {
            when (s.step) {
                SetupStep.TURN_ON,
                SetupStep.PERMISSIONS,
                SetupStep.CURSOR_CALIBRATION,
                SetupStep.APPEARANCE -> settingsRepository.setAppearance(s.colorScheme, s.textSize, s.layoutMode)
                SetupStep.NEEDS -> settingsRepository.setAccessibilityNeeds(s.needs)
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
                    SetupStep.TURN_ON,
                    SetupStep.PERMISSIONS,
                    SetupStep.CURSOR_CALIBRATION,
                    SetupStep.NEEDS -> it.copy(needs = saved.accessibilityNeeds)
                    SetupStep.APPEARANCE -> it.copy(
                        colorScheme = saved.colorScheme,
                        textSize = saved.textSize,
                        layoutMode = saved.layoutMode,
                    )

                }
            }
            advance()
        }
    }

    /** @return false when already on the first step (caller should leave the screen). */
    fun back(): Boolean {
        val s = _state.value
        if (s.step == SetupStep.CURSOR_CALIBRATION) {
            val previousAxis = s.axis.previous()
            if (previousAxis != null) {
                _state.update { it.copy(axis = previousAxis) }
                return true
            }
        }
        if (s.stepIndex == 0) return false
        _state.update { it.copy(stepIndex = it.stepIndex - 1, axis = Axis.entries.first()) }
        return true
    }

    private suspend fun advance() {
        val s = _state.value
        if (s.isLastStep) {
            if (!appearanceOnly) settingsRepository.setSetupCompleted(true)
            _state.update { it.copy(finished = true) }
        } else {
            _state.update { it.copy(stepIndex = it.stepIndex + 1, axis = Axis.entries.first()) }
        }
    }
}