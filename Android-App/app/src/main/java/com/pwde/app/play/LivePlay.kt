package com.pwde.app.play

import com.pwde.app.data.model.Game
import com.pwde.app.data.model.MappedButton
import com.pwde.app.sensors.face.FaceState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The live session over the real game, as the notification, overlay and accessibility service see it. */
data class LivePlayState(
    val active: Boolean = false,
    val game: Game? = null,
    val profileName: String? = null,
    val buttons: List<MappedButton> = emptyList(),
    val paused: Boolean = false,
    /** The user hid PWDe's floating UI; the pointer and taps still work. */
    val overlayHidden: Boolean = false,
    /** "show controls": every mapped button is labelled with what presses it, for a few seconds. */
    val controlsShown: Boolean = false,
    val face: FaceState = FaceState(),
    /** A drag is holding the screen at the pointer until "drop". */
    val dragging: Boolean = false,
    /** Latest feedback, e.g. "Pressed Skill 1" or why something was ignored. */
    val message: String? = null,
    /** The speech engine listening in game, e.g. "sherpa-onnx keyword spotter". */
    val voiceModel: String? = null,
    /** What that engine last heard, shown under the floating bubble. */
    val heard: Heard? = null,
)

/**
 * One speech result: the words, and whether they matched a command. [seq] goes up with every result,
 * so saying the same phrase twice still reads as a new hit.
 */
data class Heard(val text: String, val matched: Boolean, val seq: Int)

/**
 * App-wide hub for the live session: [PlayService] writes it, the accessibility service reads
 * [state] and performs [actions] on the real screen. One instance, on AppContainer.
 */
class LivePlay {
    private val _state = MutableStateFlow(LivePlayState())
    val state: StateFlow<LivePlayState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<GameCommand>(extraBufferCapacity = 16)

    /** Commands that act on the real screen: presses, select, touch & hold, scroll, drag and system actions. */
    val actions: SharedFlow<GameCommand> = _actions.asSharedFlow()

    private val _requests = MutableSharedFlow<GameCommand>(extraBufferCapacity = 16)

    /** Commands from outside the session, e.g. the floating bubble's pause or mode switch. */
    internal val requests: SharedFlow<GameCommand> = _requests.asSharedFlow()

    /** Ask the running session to do [command], as if the user had said it. */
    fun request(command: GameCommand) {
        _requests.tryEmit(command)
    }

    internal fun update(transform: (LivePlayState) -> LivePlayState) = _state.update(transform)

    internal fun perform(command: GameCommand) {
        _actions.tryEmit(command)
    }

    internal fun end() {
        _state.value = LivePlayState()
    }
}
