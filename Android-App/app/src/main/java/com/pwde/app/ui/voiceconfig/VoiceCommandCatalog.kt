package com.pwde.app.ui.voiceconfig

import com.pwde.app.sensors.voice.StandardCommands
import com.pwde.app.sensors.voice.VoiceCommand
import com.pwde.app.ui.components.MainTab
import com.pwde.app.ui.components.voiceCommand
import com.pwde.app.ui.controls.CURSOR_COMMANDS
import com.pwde.app.ui.controls.GESTURES_COMMANDS
import com.pwde.app.ui.controls.INPUT_COMMANDS
import com.pwde.app.ui.controls.JOYSTICK_COMMANDS
import com.pwde.app.ui.gabai.MAPPING_COMMANDS
import com.pwde.app.ui.gabai.TRIGGER_COMMANDS
import com.pwde.app.ui.games.GAME_DETAIL_COMMANDS
import com.pwde.app.ui.games.gameCommands
import com.pwde.app.ui.profile.PROFILE_COMMANDS

/** One titled section of the "All voice commands" reference. */
data class CommandGroup(val title: String, val commands: List<VoiceCommand>)

/**
 * Every voice command in the app, grouped by where it works, built from the same lists the screens
 * register (so this reference can't drift from what actually fires). Screens only listen for their
 * own group while shown; "Everywhere" and your shortcuts work on every screen.
 *
 * @param thisScreen the Voice screen's own commands.
 * @param shortcuts the user's custom phrases, already turned into commands.
 */
fun allVoiceCommandGroups(thisScreen: List<VoiceCommand>, shortcuts: List<VoiceCommand>): List<CommandGroup> = listOf(
    CommandGroup("Everywhere", StandardCommands.all),
    CommandGroup("Your shortcuts", shortcuts),
    CommandGroup("Main tabs", MainTab.entries.map { voiceCommand("tab:${it.name}", it.label) }),
    CommandGroup("Switching input mode (Controls → Input)", INPUT_COMMANDS),
    CommandGroup("Gestures (Controls → Gestures)", GESTURES_COMMANDS),
    CommandGroup("Cursor speed (Controls → Cursor speed)", CURSOR_COMMANDS),
    CommandGroup("Joystick tuning (Controls → Joystick)", JOYSTICK_COMMANDS),
    CommandGroup("Custom button mapping (GabAI)", MAPPING_COMMANDS),
    CommandGroup("Choosing how to press buttons (GabAI)", TRIGGER_COMMANDS),
    CommandGroup(
        "Games",
        gameCommands(MainTab.GAMES).filterNot { it.id.startsWith("tab:") } + GAME_DETAIL_COMMANDS,
    ),
    CommandGroup("Profile", PROFILE_COMMANDS.filterNot { it.id.startsWith("tab:") }),
    CommandGroup("This screen (Voice)", thisScreen),
).filter { it.commands.isNotEmpty() }
