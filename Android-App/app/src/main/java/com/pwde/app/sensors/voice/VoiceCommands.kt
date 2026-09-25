package com.pwde.app.sensors.voice

import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.model.VoiceShortcut

enum class CommandScope {
    /** Works on every screen (back, home, …). */
    GLOBAL,

    /** Registered by the screen currently shown; wins over a global command with the same words. */
    SCREEN,
}

/** Something the user can say. Any of [phrases] triggers it. */
data class VoiceCommand(
    val id: String,
    val phrases: List<String>,
    val scope: CommandScope = CommandScope.SCREEN,
) {
    val label: String get() = phrases.first()

    constructor(id: String, vararg phrases: String, scope: CommandScope = CommandScope.SCREEN) :
        this(id, phrases.toList(), scope)
}

/** The standard commands available everywhere, plus the user's customisable shortcuts. */
object StandardCommands {
    val BACK = VoiceCommand("back", "back", "go back", scope = CommandScope.GLOBAL)
    val HOME = VoiceCommand("home", "home", "go home", scope = CommandScope.GLOBAL)
    val NEXT = VoiceCommand("next", "next", "next page", "continue", scope = CommandScope.GLOBAL)
    val SKIP = VoiceCommand("skip", "skip", scope = CommandScope.GLOBAL)
    val SETTINGS = VoiceCommand("settings", "settings", "open settings", scope = CommandScope.GLOBAL)
    val MENU = VoiceCommand("menu", "menu", "main menu", scope = CommandScope.GLOBAL)
    val CLOSE = VoiceCommand("close", "close", scope = CommandScope.GLOBAL)

    val all = listOf(BACK, HOME, NEXT, SKIP, SETTINGS, MENU, CLOSE)

    fun shortcutId(shortcut: VoiceShortcut) = "shortcut:${shortcut.name}"

    fun shortcutOf(command: VoiceCommand): VoiceShortcut? =
        VoiceShortcut.entries.firstOrNull { shortcutId(it) == command.id }

    fun shortcuts(phrases: Map<VoiceShortcut, String>): List<VoiceCommand> =
        VoiceShortcut.entries.mapNotNull { shortcut ->
            val phrase = phrases[shortcut]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            VoiceCommand(shortcutId(shortcut), listOf(phrase), CommandScope.GLOBAL)
        }
}

/** Matches heard text against commands. Pure, so every rule here is unit-tested. */
object CommandMatcher {
    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .replace("'", "")
            .trim()
            .replace(Regex("\\s+"), " ")

    /**
     * Screen commands are tried before global ones; within a group the longest matching phrase
     * wins, so "next page" beats "next". Each speech hypothesis is tried in order.
     */
    fun match(hypotheses: List<String>, commands: List<VoiceCommand>, mode: VoiceMatchMode): VoiceCommand? {
        val screen = commands.filter { it.scope == CommandScope.SCREEN }
        val global = commands.filter { it.scope == CommandScope.GLOBAL }
        for (hypothesis in hypotheses) {
            val heard = normalize(hypothesis)
            if (heard.isEmpty()) continue
            (bestMatch(heard, screen, mode) ?: bestMatch(heard, global, mode))?.let { return it }
        }
        return null
    }

    fun match(text: String, commands: List<VoiceCommand>, mode: VoiceMatchMode): VoiceCommand? =
        match(listOf(text), commands, mode)

    private fun bestMatch(heard: String, commands: List<VoiceCommand>, mode: VoiceMatchMode): VoiceCommand? {
        var best: VoiceCommand? = null
        var bestLength = -1
        for (command in commands) {
            for (phrase in command.phrases) {
                val p = normalize(phrase)
                if (p.isEmpty()) continue
                val hit = when (mode) {
                    VoiceMatchMode.EXACT -> heard == p
                    VoiceMatchMode.WORD_ANYWHERE -> " $heard ".contains(" $p ")
                }
                if (hit && p.length > bestLength) {
                    best = command
                    bestLength = p.length
                }
            }
        }
        return best
    }
}

/**
 * Decides when a match actually fires, per utterance.
 * - IMMEDIATE: on the first (partial) transcript that matches; the same command won't fire again
 *   when the final transcript arrives.
 * - AFTER_FINISH: only on the final transcript.
 */
class VoiceActivationGate {
    private val firedThisUtterance = mutableSetOf<String>()

    fun offer(match: VoiceCommand?, isFinal: Boolean, mode: VoiceActivationMode): VoiceCommand? {
        val fire = when {
            match == null -> null
            mode == VoiceActivationMode.AFTER_FINISH -> match.takeIf { isFinal }
            match.id in firedThisUtterance -> null
            else -> match
        }
        if (fire != null) firedThisUtterance += fire.id
        if (isFinal) firedThisUtterance.clear()
        return fire
    }

    fun reset() = firedThisUtterance.clear()
}
