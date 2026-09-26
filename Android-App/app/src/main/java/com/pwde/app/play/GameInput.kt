package com.pwde.app.play

import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.voice.VoiceCommandBinding

/** What one piece of in-game input (a voice command, gesture or joystick move) asks for. */
sealed interface GameCommand {
    data class Press(val button: MappedButton) : GameCommand
    object Select : GameCommand
    object TouchHold : GameCommand
    object Pause : GameCommand
    object Resume : GameCommand
    object TogglePause : GameCommand
    object Recenter : GameCommand
    object Back : GameCommand
    object Home : GameCommand
    object Notifications : GameCommand
    object AllApps : GameCommand
    object Recents : GameCommand
    /** Swipe the screen under the pointer so content moves the way [direction] reads, e.g. DOWN shows what's below. */
    data class Scroll(val direction: ScrollDirection) : GameCommand
    /** Press and hold at the pointer, then follow the head until [Drop]. */
    object StartDrag : GameCommand
    object Drop : GameCommand
    object CursorMode : GameCommand
    object JoystickMode : GameCommand
    /** Leave the game and go back to PWDe. */
    object Exit : GameCommand
    object HideOverlay : GameCommand
    object ShowOverlay : GameCommand
    /** List every mapped button with what presses it, over the game. */
    object ShowControls : GameCommand
    object HideControls : GameCommand

    /** Nothing to do; [reason] is shown to the user. */
    data class Ignored(val reason: String) : GameCommand
}

enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

/**
 * Turns in-game input into [GameCommand]s. Shared by the simulated Playing screen and the live
 * session over the real game, so both follow the same rules. Pure, so it's unit-tested.
 */
object GameInput {
    const val BACK = "game_back"
    const val PAUSE = "game_pause"
    const val MENU = "game_menu"
    const val RESUME = "game_resume"
    const val EXIT = "game_exit"
    const val SELECT = "game_select"
    const val RECENTER = "game_recenter"
    const val HIDE_OVERLAY = "game_hide_overlay"
    const val SHOW_OVERLAY = "game_show_overlay"
    const val SHOW_CONTROLS = "game_show_controls"
    const val HIDE_CONTROLS = "game_hide_controls"
    const val HOME = "game_home"
    const val RECENTS = "game_recents"
    const val NOTIFICATIONS = "game_notifications"
    const val TOUCH_HOLD = "game_touch_hold"
    const val DRAG = "game_drag"
    const val DROP = "game_drop"
    const val CURSOR_MODE = "game_cursor_mode"
    const val JOYSTICK_MODE = "game_joystick_mode"
    private const val SCROLL = "game_scroll:"

    fun buttonCommandId(buttonId: Int) = "button:$buttonId"

    /** Also used by GabAI's mapping and test steps, so the phrase works the same everywhere. */
    val SHOW_CONTROLS_PHRASES = listOf("show controls", "show buttons", "list controls")
    val HIDE_CONTROLS_PHRASES = listOf("hide controls", "hide buttons")

    /** Always available in game, on top of the profile's own voice commands. */
    val STANDARD_BINDINGS = listOf(
        VoiceCommandBinding(BACK, listOf("back", "go back")),
        VoiceCommandBinding(PAUSE, listOf("pause", "pause game")),
        // Leaving the game ends the whole session, and in-game voice is a keyword spotter that
        // hears the mic, game audio included. So the only way out is an explicit phrase — a bare
        // "menu" or "exit" is too easy for the game's own music and voice lines to trip.
        VoiceCommandBinding(MENU, listOf("pwde menu")),
        VoiceCommandBinding(RESUME, listOf("resume", "continue game", "unpause")),
        VoiceCommandBinding(EXIT, listOf("exit game", "quit game", "exit to pwde", "stop pwde")),
        VoiceCommandBinding(SELECT, listOf("select", "tap", "click")),
        VoiceCommandBinding(RECENTER, listOf("recenter", "center", "recenter joystick", "center joystick")),
        VoiceCommandBinding(HIDE_OVERLAY, listOf("hide overlay", "hide panel")),
        VoiceCommandBinding(SHOW_OVERLAY, listOf("show overlay", "show panel")),
        VoiceCommandBinding(SHOW_CONTROLS, SHOW_CONTROLS_PHRASES),
        VoiceCommandBinding(HIDE_CONTROLS, HIDE_CONTROLS_PHRASES),
        VoiceCommandBinding(HOME, listOf("go home", "home screen")),
        VoiceCommandBinding(RECENTS, listOf("recent apps", "recents")),
        VoiceCommandBinding(NOTIFICATIONS, listOf("notifications", "open notifications")),
        VoiceCommandBinding(TOUCH_HOLD, listOf("long press", "touch and hold")),
        VoiceCommandBinding(DRAG, listOf("drag", "start drag")),
        VoiceCommandBinding(DROP, listOf("drop", "let go")),
        VoiceCommandBinding(CURSOR_MODE, listOf("cursor mode")),
        VoiceCommandBinding(JOYSTICK_MODE, listOf("joystick mode")),
    ) + ScrollDirection.entries.map { VoiceCommandBinding(SCROLL + it.name, listOf("scroll ${it.name.lowercase()}")) }

    /** The standard commands plus each button's own voice trigger. */
    fun bindings(buttons: List<MappedButton>): List<VoiceCommandBinding> = STANDARD_BINDINGS + buttonBindings(buttons)

    /** Just each button's own voice trigger. */
    fun buttonBindings(buttons: List<MappedButton>): List<VoiceCommandBinding> = buttons.mapNotNull { b ->
        b.trigger?.takeIf { it.type == TriggerType.VOICE }?.let { VoiceCommandBinding(buttonCommandId(b.id), listOf(it.value)) }
    }

    fun fromVoice(commandId: String?, rawText: String?, buttons: List<MappedButton>): GameCommand? = when (commandId) {
        null -> rawText?.let { GameCommand.Ignored("Heard \"$it\" — not a command in this game") }
        BACK -> GameCommand.Back
        PAUSE -> GameCommand.Pause
        RESUME -> GameCommand.Resume
        MENU, EXIT -> GameCommand.Exit
        SELECT -> GameCommand.Select
        RECENTER -> GameCommand.Recenter
        HIDE_OVERLAY -> GameCommand.HideOverlay
        SHOW_OVERLAY -> GameCommand.ShowOverlay
        SHOW_CONTROLS -> GameCommand.ShowControls
        HIDE_CONTROLS -> GameCommand.HideControls
        HOME -> GameCommand.Home
        RECENTS -> GameCommand.Recents
        NOTIFICATIONS -> GameCommand.Notifications
        TOUCH_HOLD -> GameCommand.TouchHold
        DRAG -> GameCommand.StartDrag
        DROP -> GameCommand.Drop
        CURSOR_MODE -> GameCommand.CursorMode
        JOYSTICK_MODE -> GameCommand.JoystickMode
        else -> ScrollDirection.entries.firstOrNull { commandId == SCROLL + it.name }?.let { GameCommand.Scroll(it) } ?: buttons.firstOrNull { buttonCommandId(it.id) == commandId }?.let { GameCommand.Press(it) }
    }

    /** A game button mapped to this gesture wins over the general gesture actions. */
    fun fromGesture(gesture: FacialGesture, buttons: List<MappedButton>, config: ControlConfig): GameCommand {
        buttonFor(buttons, TriggerType.GESTURE, gesture.name)?.let { return GameCommand.Press(it) }
        return when (config.actionFor(gesture)) {
            null -> GameCommand.Ignored("${gesture.label} — no action assigned")
            GestureAction.SELECT -> GameCommand.Select
            GestureAction.PAUSE_RESUME -> GameCommand.TogglePause
            GestureAction.RECENTER -> GameCommand.Recenter
            GestureAction.BACK -> GameCommand.Back
            GestureAction.HOME -> GameCommand.Home
            GestureAction.NOTIFICATIONS -> GameCommand.Notifications
            GestureAction.ALL_APPS -> GameCommand.AllApps
            GestureAction.TOUCH_HOLD -> GameCommand.TouchHold
        }
    }

    /** The head joystick settled on [direction]; null when no button is mapped to it. */
    fun fromJoystick(direction: JoystickDirection, buttons: List<MappedButton>): GameCommand? =
        if (direction == JoystickDirection.CENTER) null
        else buttonFor(buttons, TriggerType.JOYSTICK, direction.name)?.let { GameCommand.Press(it) }

    /** While paused only commands that control PWDe itself still work. */
    fun worksWhilePaused(command: GameCommand): Boolean = when (command) {
        GameCommand.Pause, GameCommand.Resume, GameCommand.TogglePause, GameCommand.Recenter, GameCommand.Exit,
        GameCommand.HideOverlay, GameCommand.ShowOverlay, GameCommand.ShowControls, GameCommand.HideControls, GameCommand.CursorMode, GameCommand.JoystickMode,
        GameCommand.Drop, is GameCommand.Ignored -> true
        else -> false
    }

    private fun buttonFor(buttons: List<MappedButton>, type: TriggerType, value: String): MappedButton? =
        buttons.firstOrNull { it.trigger?.type == type && it.trigger.value == value }
}
