package com.pwde.app.play

import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.voice.InGameVoiceEngine
import com.pwde.app.data.prefs.InputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One live session over the real game: keeps face tracking and the in-game voice engine running
 * while PWDe is in the background, turns their input into [GameCommand]s through [GameInput], and
 * publishes everything on [LivePlay]. Screen actions are performed by the accessibility service.
 */
class LiveGameSession(
    private val livePlay: LivePlay,
    private val faceTracking: FaceTrackingManager,
    private val voiceEngine: InGameVoiceEngine,
    private val profileRepository: ProfileRepository,
    private val controlsRepository: ControlsRepository,
    private val settingsRepository: SettingsRepository,
    /** The user asked to leave the game ("exit", or an Exit gesture). */
    private val onExit: () -> Unit,
) {
    private var config = ControlConfig()
    private var lastDirection = JoystickDirection.CENTER

    /** Runs until cancelled. */
    /** The session's own scope, for work that outlives one command (saving the input mode). */
    private var scope: CoroutineScope? = null

    suspend fun run(game: Game, profileId: Long?) {
        val profile = profileId?.let { profileRepository.getGameProfile(it) }
        if (profile != null) applyProfileCalibration(profile, profileRepository, controlsRepository, settingsRepository)
        val buttons = profile?.let { ControlJson.decodeButtons(it.buttonMappingsJson) }.orEmpty()
        livePlay.update { LivePlayState(active = true, game = game, profileName = profile?.profileName, buttons = buttons) }
        voiceEngine.loadCommands(GameInput.bindings(buttons))
        voiceEngine.start()
        try {
            coroutineScope {
                scope = this
                launch { controlsRepository.config.collect { config = it } }
                // Collecting the state is what keeps the camera running in the background.
                launch { faceTracking.state.collect { face -> livePlay.update { it.copy(face = face) } } }
                launch {
                    faceTracking.state.map { it.joystick.direction }.distinctUntilChanged().collect(::onJoystickDirection)
                }
                launch { faceTracking.gestureEvents.collect(::onGesture) }
                launch { voiceEngine.results.collect { onVoice(it.commandId, it.rawText) } }
                launch { livePlay.requests.collect(::runUnlessPaused) }
            }
        } finally {
            scope = null
            voiceEngine.stop()
            livePlay.end()
        }
    }

    fun togglePause() = execute(GameCommand.TogglePause)

    fun recenter() = execute(GameCommand.Recenter)

    private fun onJoystickDirection(direction: JoystickDirection) {
        if (direction == lastDirection) return
        lastDirection = direction
        if (livePlay.state.value.paused) return
        GameInput.fromJoystick(direction, livePlay.state.value.buttons)?.let(::execute)
    }

    private fun onVoice(commandId: String?, rawText: String?) {
        GameInput.fromVoice(commandId, rawText, livePlay.state.value.buttons)?.let(::runUnlessPaused)
    }

    private fun onGesture(gesture: FacialGesture) =
        runUnlessPaused(GameInput.fromGesture(gesture, livePlay.state.value.buttons, config))

    private fun runUnlessPaused(command: GameCommand) {
        if (livePlay.state.value.paused && !GameInput.worksWhilePaused(command)) {
            return message("Paused — say \"resume\" first")
        }
        execute(command)
    }

    private fun execute(command: GameCommand) {
        when (command) {
            GameCommand.Pause -> setPaused(true)
            GameCommand.Resume -> setPaused(false)
            GameCommand.TogglePause -> setPaused(!livePlay.state.value.paused)
            GameCommand.Recenter -> {
                faceTracking.recenterCursor()
                message("Recentered")
            }
            GameCommand.Exit -> onExit()
            GameCommand.HideOverlay -> livePlay.update { it.copy(overlayHidden = true) }
            GameCommand.ShowOverlay -> livePlay.update { it.copy(overlayHidden = false) }
            is GameCommand.Ignored -> message(command.reason)
            // Presses, select, touch & hold and system actions happen on the real screen.
            is GameCommand.Press -> {
                livePlay.perform(command)
                message("Pressed ${command.button.label}")
            }
            GameCommand.CursorMode -> switchMode(InputMode.HEAD_FACE, "Cursor mode")
            GameCommand.JoystickMode -> switchMode(InputMode.JOYSTICK, "Joystick mode")
            GameCommand.StartDrag -> {
                livePlay.update { it.copy(dragging = true) }
                livePlay.perform(command)
                message("Dragging — say \"drop\" to let go")
            }
            GameCommand.Drop -> {
                livePlay.update { it.copy(dragging = false) }
                livePlay.perform(command)
            }
            GameCommand.Select, GameCommand.TouchHold, GameCommand.Back, GameCommand.Home,
            GameCommand.Notifications, GameCommand.AllApps, GameCommand.Recents, is GameCommand.Scroll -> livePlay.perform(command)
        }
    }

    private fun switchMode(mode: InputMode, label: String) {
        scope?.launch { settingsRepository.setInputMode(mode) }
        message(label)
    }

    private fun setPaused(paused: Boolean) {
        livePlay.update { it.copy(paused = paused) }
        message(if (paused) "Paused" else "Resumed")
    }

    private fun message(text: String) = livePlay.update { it.copy(message = text) }
}
