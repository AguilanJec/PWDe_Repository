package com.pwde.app.play

import android.util.Log
import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.voice.InGameVoiceEngine
import com.pwde.app.data.prefs.InputMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** Hides the "show controls" labels again; restarted by every "show controls". */
    private var controlsTimeout: Job? = null

    suspend fun run(game: Game, profileId: Long?) {
        val profile = profileId?.let { profileRepository.getGameProfile(it) }
        if (profile != null) applyProfileCalibration(profile, profileRepository, controlsRepository, settingsRepository)
        val buttons = profile?.let { ControlJson.decodeButtons(it.buttonMappingsJson) }.orEmpty()
        livePlay.update {
            LivePlayState(
                active = true,
                game = game,
                profileName = profile?.profileName,
                buttons = buttons,
                voiceModel = voiceEngine.modelLabel.substringBefore(" ·"),
            )
        }
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
                launch {
                    voiceEngine.results.collect {
                        showHeard(it.rawText, matched = it.commandId != null)
                        onVoice(it.commandId, it.rawText)
                    }
                }
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
        val command = GameInput.fromVoice(commandId, rawText, livePlay.state.value.buttons)
        Log.i(TAG, "Voice \"$rawText\" ($commandId) -> $command")
        command?.let(::runUnlessPaused)
    }

    private fun showHeard(text: String?, matched: Boolean) {
        if (text.isNullOrBlank()) return
        livePlay.update { it.copy(heard = Heard(text, matched, (it.heard?.seq ?: 0) + 1)) }
    }

    private fun onGesture(gesture: FacialGesture) =
        runUnlessPaused(GameInput.fromGesture(gesture, livePlay.state.value.buttons, config))

    private fun runUnlessPaused(command: GameCommand) {
        if (livePlay.state.value.paused && !GameInput.worksWhilePaused(command)) {
            Log.i(TAG, "Dropped $command: paused")
            return message("Paused — say \"resume\" first")
        }
        execute(command)
    }

    private fun execute(command: GameCommand) {
        when (command) {
            GameCommand.Pause -> setPaused(true)
            GameCommand.Resume -> setPaused(false)
            GameCommand.TogglePause -> setPaused(!livePlay.state.value.paused)
            GameCommand.Recenter -> recenterForMode()
            GameCommand.Exit -> onExit()
            GameCommand.HideOverlay -> livePlay.update { it.copy(overlayHidden = true) }
            GameCommand.ShowOverlay -> livePlay.update { it.copy(overlayHidden = false) }
            GameCommand.ShowControls -> showControls()
            GameCommand.HideControls -> hideControls()
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

    /** Cursor mode: the pointer back to the middle. Joystick mode: where the head is now becomes the stick's neutral. */
    private fun recenterForMode() {
        if (livePlay.state.value.face.outputMode != FaceOutputMode.JOYSTICK) {
            faceTracking.recenterCursor()
            return message("Recentered")
        }
        scope?.launch {
            message(if (faceTracking.captureJoystickCenter()) "Joystick recentered" else "Can't see your face — look at the camera and try again")
        }
    }

    private fun showControls() {
        val buttons = livePlay.state.value.buttons
        if (buttons.isEmpty()) return message("This profile has no mapped buttons")
        livePlay.update { it.copy(controlsShown = true) }
        message(buttons.joinToString(" · ") { "${it.label}: ${it.trigger?.shortLabel() ?: "—"}" })
        controlsTimeout?.cancel()
        controlsTimeout = scope?.launch {
            delay(CONTROLS_SHOWN_MS)
            livePlay.update { it.copy(controlsShown = false) }
        }
    }

    private fun hideControls() {
        controlsTimeout?.cancel()
        livePlay.update { it.copy(controlsShown = false) }
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

    private companion object {
        const val TAG = "PwdeLiveSession"
        const val CONTROLS_SHOWN_MS = 10_000L
    }
}
