package com.pwde.app.ui.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.gabai.GabAiRepository
import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.GameProfile
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.local.inputModeOrDefault
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.voice.InGameVoiceEngine
import com.pwde.app.sensors.voice.InGameVoiceState
import com.pwde.app.sensors.voice.VoiceCommandBinding
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Something the overlay did, shown briefly. [id] increases so repeated actions still animate. */
data class OverlayEvent(val id: Long, val text: String, val kind: Kind, val buttonId: Int? = null) {
    enum class Kind { ACTION, SELECT, BUTTON, IGNORED }
}

data class GameplayUiState(
    val profile: GameProfile? = null,
    val calibrationName: String? = null,
    val buttons: List<MappedButton> = emptyList(),
    val screenshot: ImageBitmap? = null,
)

/**
 * The play overlay. Face input comes from [FaceTrackingManager]; voice comes only from
 * [InGameVoiceEngine] — this class never names a concrete engine or the platform recognizer,
 * so swapping in a dedicated engine touches only the AppContainer binding.
 */
class GameplayViewModel(
    faceTracking: FaceTrackingManager,
    private val voiceEngine: InGameVoiceEngine,
    controlsRepository: ControlsRepository,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val gabAiRepository: GabAiRepository,
    val game: Game?,
    private val profileId: Long?,
) : FaceTrackingViewModel(faceTracking) {
    val voice: StateFlow<InGameVoiceState> = voiceEngine.state

    private val config: StateFlow<ControlConfig> = controlsRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControlConfig())

    private val _ui = MutableStateFlow(GameplayUiState())
    val ui: StateFlow<GameplayUiState> = _ui.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _lastEvent = MutableStateFlow<OverlayEvent?>(null)
    val lastEvent: StateFlow<OverlayEvent?> = _lastEvent.asStateFlow()

    private val _exit = Channel<Unit>(Channel.CONFLATED)
    val exitRequests: Flow<Unit> = _exit.receiveAsFlow()

    private var eventId = 0L
    private var lastDirection = JoystickDirection.CENTER

    init {
        voiceEngine.loadCommands(STANDARD_BINDINGS)
        viewModelScope.launch {
            val profile = profileId?.let { profileRepository.getGameProfile(it) }
                ?: game?.let { profileRepository.gameProfilesFor(it.id).firstOrNull()?.firstOrNull() }
            if (profile != null) loadProfile(profile, controlsRepository)
        }
        viewModelScope.launch { voiceEngine.results.collect { onVoice(it.commandId, it.rawText) } }
        viewModelScope.launch { faceTracking.gestureEvents.collect(::onGesture) }
    }

    private suspend fun loadProfile(profile: GameProfile, controlsRepository: ControlsRepository) {
        val buttons = ControlJson.decodeButtons(profile.buttonMappingsJson)
        val calibration = profile.calibrationProfileId?.let { profileRepository.getCalibrationProfile(it) }
        if (calibration != null) {
            // Play with the calibration this game profile was made with.
            controlsRepository.applyCalibration(calibration)
            if (settingsRepository.settings.first().inputMode != calibration.inputModeOrDefault) {
                settingsRepository.setInputMode(calibration.inputModeOrDefault)
            }
        }
        _ui.update { it.copy(profile = profile, calibrationName = calibration?.name, buttons = buttons) }
        // Only this game's commands can be recognized while playing.
        voiceEngine.loadCommands(
            STANDARD_BINDINGS + buttons.mapNotNull { b ->
                b.trigger?.takeIf { it.type == TriggerType.VOICE }?.let { VoiceCommandBinding(buttonCommandId(b.id), listOf(it.value)) }
            },
        )
        val bitmap = gabAiRepository.loadScreenshot(profile.thumbnailPath)?.asImageBitmap()
        _ui.update { it.copy(screenshot = bitmap) }
    }

    /** Screen visible: the game's voice engine takes the mic. */
    fun onScreenStarted() = voiceEngine.start()

    /** Screen hidden: give the mic back to the rest of PWDe. */
    fun onScreenStopped() = voiceEngine.stop()

    fun submitText(text: String) = voiceEngine.submitText(text)

    fun togglePause() {
        _paused.value = !_paused.value
        post(if (_paused.value) "Paused — gestures and voice won't play" else "Resumed", OverlayEvent.Kind.ACTION)
    }

    fun select() {
        if (_paused.value) return post("Paused — say \"resume\" first", OverlayEvent.Kind.IGNORED)
        post("Select", OverlayEvent.Kind.SELECT)
    }

    fun exit() {
        _exit.trySend(Unit)
    }

    /** Called by the screen when the head joystick settles on a new direction. */
    fun onJoystickDirection(direction: JoystickDirection) {
        if (direction == lastDirection) return
        lastDirection = direction
        if (direction == JoystickDirection.CENTER || _paused.value) return
        buttonFor(TriggerType.JOYSTICK, direction.name)?.let(::press)
    }

    private fun onVoice(commandId: String?, rawText: String?) {
        if (commandId == null) {
            rawText?.let { post("Heard \"$it\" — not a command in this game", OverlayEvent.Kind.IGNORED) }
            return
        }
        when (commandId) {
            PAUSE -> if (!_paused.value) togglePause()
            RESUME -> if (_paused.value) togglePause()
            BACK, MENU, EXIT -> exit()
            SELECT -> select()
            RECENTER -> recenter()
            else -> {
                if (_paused.value) return post("Paused — say \"resume\" first", OverlayEvent.Kind.IGNORED)
                _ui.value.buttons.firstOrNull { buttonCommandId(it.id) == commandId }?.let(::press)
            }
        }
    }

    private fun onGesture(gesture: FacialGesture) {
        // A game button mapped to this gesture wins over the general gesture actions.
        buttonFor(TriggerType.GESTURE, gesture.name)?.let { button ->
            if (_paused.value) return post("${gesture.label} ignored while paused", OverlayEvent.Kind.IGNORED)
            return press(button)
        }
        val action = config.value.actionFor(gesture)
            ?: return post("${gesture.label} — no action assigned", OverlayEvent.Kind.IGNORED)
        if (_paused.value && action != GestureAction.PAUSE_RESUME) {
            return post("${gesture.label} ignored while paused", OverlayEvent.Kind.IGNORED)
        }
        when (action) {
            GestureAction.SELECT -> select()
            GestureAction.PAUSE_RESUME -> togglePause()
            GestureAction.RECENTER -> recenter()
            GestureAction.BACK, GestureAction.HOME -> exit()
            // Phone-wide actions need system access PWDe doesn't have; they act in the overlay only.
            GestureAction.NOTIFICATIONS, GestureAction.ALL_APPS, GestureAction.TOUCH_HOLD ->
                post("${action.label} (in PWDe's overlay only)", OverlayEvent.Kind.ACTION)
        }
    }

    private fun buttonFor(type: TriggerType, value: String): MappedButton? =
        _ui.value.buttons.firstOrNull { it.trigger?.type == type && it.trigger.value == value }

    private fun press(button: MappedButton) = post("Pressed ${button.label}", OverlayEvent.Kind.BUTTON, button.id)

    private fun recenter() {
        recenterCursor()
        post("Recentered", OverlayEvent.Kind.ACTION)
    }

    private fun post(text: String, kind: OverlayEvent.Kind, buttonId: Int? = null) {
        _lastEvent.value = OverlayEvent(++eventId, text, kind, buttonId)
    }

    override fun onCleared() {
        voiceEngine.stop()
    }

    companion object {
        const val BACK = "game_back"
        const val PAUSE = "game_pause"
        const val MENU = "game_menu"
        const val RESUME = "game_resume"
        const val EXIT = "game_exit"
        const val SELECT = "game_select"
        const val RECENTER = "game_recenter"

        fun buttonCommandId(buttonId: Int) = "button:$buttonId"

        /** Always available in game, on top of the profile's own voice commands. */
        val STANDARD_BINDINGS = listOf(
            VoiceCommandBinding(BACK, listOf("back", "go back")),
            VoiceCommandBinding(PAUSE, listOf("pause", "pause game")),
            VoiceCommandBinding(MENU, listOf("menu", "main menu")),
            VoiceCommandBinding(RESUME, listOf("resume", "continue game", "unpause")),
            VoiceCommandBinding(EXIT, listOf("exit", "exit game", "quit", "exit to pwde")),
            VoiceCommandBinding(SELECT, listOf("select", "tap", "click")),
            VoiceCommandBinding(RECENTER, listOf("recenter", "center")),
        )
    }
}
