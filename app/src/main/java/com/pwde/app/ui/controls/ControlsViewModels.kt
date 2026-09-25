package com.pwde.app.ui.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InputModeViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val inputMode: StateFlow<InputMode?> = settingsRepository.settings
        .map { it.inputMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(mode: InputMode) {
        viewModelScope.launch { settingsRepository.setInputMode(mode) }
    }
}

/** E2/E3 Gestures: which face move triggers each action. Reads/writes Room via [ControlsRepository]. */
class GesturesViewModel(controlsRepository: ControlsRepository) : ViewModel() {
    val config: StateFlow<ControlConfig?> = controlsRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

data class ChooseGestureUiState(
    val action: GestureAction,
    val selected: FacialGesture? = null,
    /** Gesture → other actions already using it. */
    val usedBy: Map<FacialGesture, List<GestureAction>> = emptyMap(),
)

/** E4/E5 Choose a gesture for one action, from the static catalog. */
class ChooseGestureViewModel(
    private val controlsRepository: ControlsRepository,
    private val action: GestureAction,
) : ViewModel() {
    val state: StateFlow<ChooseGestureUiState> = controlsRepository.config
        .map { config ->
            ChooseGestureUiState(
                action = action,
                selected = config.gestureAssignments[action],
                usedBy = FacialGesture.entries.associateWith { config.conflictsFor(action, it) }
                    .filterValues { it.isNotEmpty() },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChooseGestureUiState(action))

    fun select(gesture: FacialGesture) {
        viewModelScope.launch { controlsRepository.setGesture(action, gesture) }
    }

    fun clear() {
        viewModelScope.launch { controlsRepository.setGesture(action, null) }
    }
}
