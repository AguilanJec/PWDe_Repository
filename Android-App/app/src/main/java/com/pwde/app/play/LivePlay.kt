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
    val face: FaceState = FaceState(),
    /** Latest feedback, e.g. "Pressed Skill 1" or why something was ignored. */
    val message: String? = null,
)

/**
 * App-wide hub for the live session: [PlayService] writes it, the accessibility service reads
 * [state] and performs [actions] on the real screen. One instance, on AppContainer.
 */
class LivePlay {
    private val _state = MutableStateFlow(LivePlayState())
    val state: StateFlow<LivePlayState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<GameCommand>(extraBufferCapacity = 16)

    /** Commands that act on the real screen: presses, select, touch & hold, and system actions. */
    val actions: SharedFlow<GameCommand> = _actions.asSharedFlow()

    internal fun update(transform: (LivePlayState) -> LivePlayState) = _state.update(transform)

    internal fun perform(command: GameCommand) {
        _actions.tryEmit(command)
    }

    internal fun end() {
        _state.value = LivePlayState()
    }
}
