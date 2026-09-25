package com.pwde.app.ui.gameplay

import androidx.lifecycle.viewModelScope
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.GestureAction
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.voice.VoiceCommand
import com.pwde.app.sensors.voice.VoiceCommandManager
import com.pwde.app.sensors.voice.VoiceState
import com.pwde.app.ui.common.FaceTrackingViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Something the overlay did, shown briefly. [id] increases so repeated actions still animate. */
data class OverlayEvent(val id: Long, val text: String, val kind: Kind) {
    enum class Kind { ACTION, SELECT, IGNORED }
}

/**
 * The play overlay over a simulated game. Face input comes from [FaceTrackingManager]; voice
 * comes only through [VoiceCommandManager] (never SpeechRecognizer directly), so Prompt 3 can swap
 * in a dedicated in-game voice engine behind that interface without changing this ViewModel.
 */
class GameplayViewModel(
    faceTracking: FaceTrackingManager,
    private val voiceCommandManager: VoiceCommandManager,
    controlsRepository: ControlsRepository,
    val game: Game?,
) : FaceTrackingViewModel(faceTracking) {
    val voice: StateFlow<VoiceState> = voiceCommandManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), voiceCommandManager.state.value)

    private val config: StateFlow<ControlConfig> = controlsRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, ControlConfig())

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _lastEvent = MutableStateFlow<OverlayEvent?>(null)
    val lastEvent: StateFlow<OverlayEvent?> = _lastEvent.asStateFlow()

    private val _exit = Channel<Unit>(Channel.CONFLATED)
    val exitRequests: Flow<Unit> = _exit.receiveAsFlow()

    private var eventId = 0L

    init {
        voiceCommandManager.setScreenCommands(this, COMMANDS)
        viewModelScope.launch {
            voiceCommandManager.results.collect { result ->
                val command = result.command ?: return@collect
                if (COMMANDS.any { it.id == command.id }) onVoiceCommand(command.id)
            }
        }
        viewModelScope.launch { faceTracking.gestureEvents.collect(::onGesture) }
    }

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

    private fun onVoiceCommand(id: String) {
        when (id) {
            PAUSE -> if (!_paused.value) togglePause()
            RESUME -> if (_paused.value) togglePause()
            EXIT -> exit()
            SELECT -> select()
            RECENTER -> recenter()
        }
    }

    private fun onGesture(gesture: FacialGesture) {
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

    private fun recenter() {
        recenterCursor()
        post("Recentered", OverlayEvent.Kind.ACTION)
    }

    private fun post(text: String, kind: OverlayEvent.Kind) {
        _lastEvent.value = OverlayEvent(++eventId, text, kind)
    }

    override fun onCleared() {
        voiceCommandManager.clearScreenCommands(this)
    }

    companion object {
        const val PAUSE = "game_pause"
        const val RESUME = "game_resume"
        const val EXIT = "game_exit"
        const val SELECT = "game_select"
        const val RECENTER = "game_recenter"

        val COMMANDS = listOf(
            VoiceCommand(PAUSE, "pause", "pause game"),
            VoiceCommand(RESUME, "resume", "continue game", "unpause"),
            VoiceCommand(EXIT, "exit", "exit game", "quit", "exit to pwde"),
            VoiceCommand(SELECT, "select", "tap", "click"),
            VoiceCommand(RECENTER, "recenter", "center"),
        )
    }
}
