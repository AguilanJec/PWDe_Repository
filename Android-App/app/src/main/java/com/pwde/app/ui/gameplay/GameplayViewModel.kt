package com.pwde.app.ui.gameplay

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.gabai.GabAiRepository
import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.GameProfile
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.play.GameCommand
import com.pwde.app.play.GameInput
import com.pwde.app.play.LivePlay
import com.pwde.app.play.applyProfileCalibration
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.voice.InGameVoiceEngine
import com.pwde.app.sensors.voice.InGameVoiceState
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.Job
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
    private val livePlay: LivePlay? = null,
) : FaceTrackingViewModel(faceTracking) {
    val voice: StateFlow<InGameVoiceState> = voiceEngine.state

    private val config: StateFlow<ControlConfig> = controlsRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControlConfig())

    private val _ui = MutableStateFlow(GameplayUiState())
    val ui: StateFlow<GameplayUiState> = _ui.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Status pills and the info panel are hidden so the game (or screenshot) shows through. */
    private val _overlayHidden = MutableStateFlow(false)
    val overlayHidden: StateFlow<Boolean> = _overlayHidden.asStateFlow()

    private val _lastEvent = MutableStateFlow<OverlayEvent?>(null)
    val lastEvent: StateFlow<OverlayEvent?> = _lastEvent.asStateFlow()

    private val _exit = Channel<Unit>(Channel.CONFLATED)
    val exitRequests: Flow<Unit> = _exit.receiveAsFlow()

    private var eventId = 0L
    private var lastDirection = JoystickDirection.CENTER

    init {
        voiceEngine.loadCommands(GameInput.STANDARD_BINDINGS)
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
        val calibration = applyProfileCalibration(profile, profileRepository, controlsRepository, settingsRepository)
        _ui.update { it.copy(profile = profile, calibrationName = calibration?.name, buttons = buttons) }
        // Only this game's commands can be recognized while playing.
        voiceEngine.loadCommands(GameInput.bindings(buttons))
        val bitmap = gabAiRepository.loadScreenshot(profile.thumbnailPath)?.asImageBitmap()
        _ui.update { it.copy(screenshot = bitmap) }
    }

    /**
     * Screen visible: the game's voice engine takes the mic — once a live session over the real
     * game has finished stopping, since it shares the engine.
     */
    fun onScreenStarted() {
        voiceStart?.cancel()
        voiceStart = viewModelScope.launch {
            livePlay?.state?.first { !it.active }
            voiceEngine.start()
        }
    }

    private var voiceStart: Job? = null

    /** Screen hidden: give the mic back to the rest of PWDe. */
    fun onScreenStopped() {
        voiceStart?.cancel()
        voiceEngine.stop()
    }

    fun submitText(text: String) = voiceEngine.submitText(text)

    fun togglePause() {
        _paused.value = !_paused.value
        post(if (_paused.value) "Paused — gestures and voice won't play" else "Resumed", OverlayEvent.Kind.ACTION)
    }

    fun setOverlayHidden(hidden: Boolean) {
        if (_overlayHidden.value == hidden) return
        _overlayHidden.value = hidden
        post(if (hidden) "Overlay hidden — say \"show overlay\" to bring it back" else "Overlay shown", OverlayEvent.Kind.ACTION)
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
        if (_paused.value) return
        GameInput.fromJoystick(direction, _ui.value.buttons)?.let(::execute)
    }

    private fun onVoice(commandId: String?, rawText: String?) {
        val command = GameInput.fromVoice(commandId, rawText, _ui.value.buttons) ?: return
        // Voice "back" always leaves the preview, even while paused.
        if (command == GameCommand.Back) return exit()
        if (_paused.value && !GameInput.worksWhilePaused(command)) {
            return post("Paused — say \"resume\" first", OverlayEvent.Kind.IGNORED)
        }
        execute(command)
    }

    private fun onGesture(gesture: FacialGesture) {
        val command = GameInput.fromGesture(gesture, _ui.value.buttons, config.value)
        if (_paused.value && !GameInput.worksWhilePaused(command)) {
            return post("${gesture.label} ignored while paused", OverlayEvent.Kind.IGNORED)
        }
        execute(command)
    }

    private fun execute(command: GameCommand) {
        when (command) {
            is GameCommand.Press -> post("Pressed ${command.button.label}", OverlayEvent.Kind.BUTTON, command.button.id)
            GameCommand.Select -> select()
            GameCommand.Pause -> if (!_paused.value) togglePause()
            GameCommand.Resume -> if (_paused.value) togglePause()
            GameCommand.TogglePause -> togglePause()
            GameCommand.Recenter -> recenter()
            GameCommand.Back, GameCommand.Home, GameCommand.Exit -> exit()
            GameCommand.HideOverlay -> setOverlayHidden(true)
            GameCommand.ShowOverlay -> setOverlayHidden(false)
            // Phone-wide actions only act in the real game; the preview just shows them.
            GameCommand.Notifications -> post("Notifications (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.AllApps -> post("All apps (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.TouchHold -> post("Touch & hold (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.Recents -> post("Recent apps (in the real game only)", OverlayEvent.Kind.ACTION)
            is GameCommand.Scroll -> post("Scroll ${command.direction.name.lowercase()} (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.StartDrag -> post("Drag (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.Drop -> post("Drop (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.CursorMode -> post("Cursor mode (in the real game only)", OverlayEvent.Kind.ACTION)
            GameCommand.JoystickMode -> post("Joystick mode (in the real game only)", OverlayEvent.Kind.ACTION)
            is GameCommand.Ignored -> post(command.reason, OverlayEvent.Kind.IGNORED)
        }
    }

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
}
