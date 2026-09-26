package com.pwde.app.ui.controls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.DEFAULT_LEVEL
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.JoystickSource
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.MAX_LEVEL
import com.pwde.app.data.model.MIN_LEVEL
import com.pwde.app.data.model.isEnabledBy
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class InputModeViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val inputMode: StateFlow<InputMode?> = settingsRepository.settings
        .map { it.inputMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What steers the joystick; only meaningful while [inputMode] is [InputMode.JOYSTICK]. */
    val joystickSource: StateFlow<JoystickSource?> = settingsRepository.settings
        .map { it.joystickSource }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(mode: InputMode) {
        viewModelScope.launch { settingsRepository.setInputMode(mode) }
    }

    fun selectSource(source: JoystickSource) {
        viewModelScope.launch { settingsRepository.setJoystickSource(source) }
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
    val sensitivity: Map<FacialGesture, Int> = emptyMap(),
    /** As [ControlConfig.enabledGestures]: null when the calibration never ran the gesture test. */
    val enabledGestures: Set<FacialGesture>? = null,
) {
    fun sensitivityOf(gesture: FacialGesture) = sensitivity[gesture] ?: DEFAULT_LEVEL

    fun isEnabled(gesture: FacialGesture) = gesture.isEnabledBy(enabledGestures)
}

/** E4/E5 Choose a gesture for one action, tune its sensitivity and try it live. */
class ChooseGestureViewModel(
    private val controlsRepository: ControlsRepository,
    faceTracking: FaceTrackingManager,
    private val action: GestureAction,
) : FaceTrackingViewModel(faceTracking) {
    val state: StateFlow<ChooseGestureUiState> = controlsRepository.config
        .map { config ->
            ChooseGestureUiState(
                action = action,
                selected = config.gestureAssignments[action],
                usedBy = FacialGesture.entries.associateWith { config.conflictsFor(action, it) }
                    .filterValues { it.isNotEmpty() },
                sensitivity = config.gestureSensitivity,
                enabledGestures = config.enabledGestures,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChooseGestureUiState(action))

    fun select(gesture: FacialGesture) {
        viewModelScope.launch { controlsRepository.setGesture(action, gesture) }
    }

    fun clear() {
        viewModelScope.launch { controlsRepository.setGesture(action, null) }
    }

    fun setSensitivity(gesture: FacialGesture, level: Int) {
        viewModelScope.launch { controlsRepository.setGestureSensitivity(gesture, level) }
    }
}

/** E6–E8 Cursor speed: live pointer from head tracking, per-direction speeds saved to Room. */
class CursorSpeedViewModel(
    private val controlsRepository: ControlsRepository,
    faceTracking: FaceTrackingManager,
) : FaceTrackingViewModel(faceTracking) {
    val tuning: StateFlow<CursorTuning?> = controlsRepository.config
        .map { it.cursor }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (CursorTuning) -> CursorTuning) {
        val current = tuning.value ?: return
        viewModelScope.launch { controlsRepository.setCursorTuning(transform(current)) }
    }

    /** Basic mode: one speed for all four directions. */
    fun setOverallSpeed(level: Int) = update { CursorTuning(level, level, level, level, it.smoothing) }

    fun changeOverallSpeed(delta: Int) {
        val current = tuning.value ?: return
        setOverallSpeed((overallSpeed(current) + delta).coerceIn(MIN_LEVEL, MAX_LEVEL))
    }

    companion object {
        fun overallSpeed(t: CursorTuning): Int = ((t.speedUp + t.speedDown + t.speedLeft + t.speedRight) / 4f).roundToInt()
    }
}

data class JoystickUiMessage(val text: String, val isError: Boolean = false)

/** E9/E10 Joystick: live head or gyro joystick; size, sensitivity, dead zone and center saved to Room. */
class JoystickViewModel(
    private val controlsRepository: ControlsRepository,
    private val settingsRepository: SettingsRepository,
    faceTracking: FaceTrackingManager,
) : FaceTrackingViewModel(faceTracking) {
    val tuning: StateFlow<JoystickTuning?> = controlsRepository.config
        .map { it.joystick }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What steers this joystick, so the screen says what is actually happening. */
    val source: StateFlow<JoystickSource?> = settingsRepository.settings
        .map { it.joystickSource }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The stick is damped by the same head-jitter filter the pointer uses, so this is the cursor's
     * smoothing level — surfaced here too rather than hidden, because it is what steadies the stick.
     */
    val smoothing: StateFlow<Int?> = controlsRepository.config
        .map { it.cursor.smoothing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<JoystickUiMessage?>(null)
    val message: StateFlow<JoystickUiMessage?> = _message.asStateFlow()

    fun update(transform: (JoystickTuning) -> JoystickTuning) {
        val current = tuning.value ?: return
        viewModelScope.launch { controlsRepository.setJoystickTuning(transform(current)) }
    }

    fun setSmoothing(level: Int) {
        viewModelScope.launch {
            val cursor = controlsRepository.config.first().cursor
            controlsRepository.setCursorTuning(cursor.copy(smoothing = level.coerceIn(MIN_LEVEL, MAX_LEVEL)))
        }
    }

    fun setCenterHere() {
        viewModelScope.launch {
            // Gyro's center is how the phone is being held, so the same call means "re-take the
            // phone's baseline"; only the head keeps an angle in Room.
            val gyro = (source.value ?: JoystickSource.HEAD) == JoystickSource.GYRO
            val captured = faceTracking.captureJoystickCenter()
            _message.value = when {
                captured && gyro -> JoystickUiMessage("Center saved. Hold your phone like this to keep the joystick still.")
                captured -> JoystickUiMessage("Center saved. Hold your head like this to keep the joystick still.")
                gyro -> JoystickUiMessage("This phone has no gyroscope, so gyro tracking can't run.", isError = true)
                else -> JoystickUiMessage("No head found — face the camera, then try again.", isError = true)
            }
        }
    }

    fun resetCenter() {
        viewModelScope.launch {
            controlsRepository.setJoystickCenter(0f, 0f)
            _message.value = JoystickUiMessage("Center reset to straight ahead.")
        }
    }
}
