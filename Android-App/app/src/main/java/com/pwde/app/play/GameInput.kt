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
    /** Leave the game and go back to PWDe. */
    object Exit : GameCommand
    object HideOverlay : GameCommand
    object ShowOverlay : GameCommand

    /** Nothing to do; [reason] is shown to the user. */
    data class Ignored(val reason: String) : GameCommand
}

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

    fun buttonCommandId(buttonId: Int) = "button:$buttonId"

    /** Always available in game, on top of the profile's own voice commands. */
    val STANDARD_BINDINGS = listOf(
        VoiceCommandBinding(BACK, listOf("back", "go back")),
        VoiceCommandBinding(PAUSE, listOf("pause", "pause game")),
        VoiceCommandBinding(MENU, listOf("menu", "main menu")),
        VoiceCommandBinding(RESUME, listOf("resume", "continue game", "unpause")),
        VoiceCommandBinding(EXIT, listOf("exit", "exit game", "quit", "exit to pwde", "stop pwde")),
        VoiceCommandBinding(SELECT, listOf("select", "tap", "click")),
        VoiceCommandBinding(RECENTER, listOf("recenter", "center")),
        VoiceCommandBinding(HIDE_OVERLAY, listOf("hide overlay", "hide panel")),
        VoiceCommandBinding(SHOW_OVERLAY, listOf("show overlay", "show panel")),
    )

    /** The standard commands plus each button's own voice trigger. */
    fun bindings(buttons: List<MappedButton>): List<VoiceCommandBinding> =
        STANDARD_BINDINGS + buttons.mapNotNull { b ->
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
        else -> buttons.firstOrNull { buttonCommandId(it.id) == commandId }?.let { GameCommand.Press(it) }
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
        GameCommand.HideOverlay, GameCommand.ShowOverlay, is GameCommand.Ignored -> true
        else -> false
    }

    private fun buttonFor(buttons: List<MappedButton>, type: TriggerType, value: String): MappedButton? =
        buttons.firstOrNull { it.trigger?.type == type && it.trigger.value == value }
}
