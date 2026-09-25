package com.pwde.app.sensors

import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.model.VoiceShortcut
import com.pwde.app.sensors.voice.CommandMatcher
import com.pwde.app.sensors.voice.CommandScope
import com.pwde.app.sensors.voice.StandardCommands
import com.pwde.app.sensors.voice.VoiceActivationGate
import com.pwde.app.sensors.voice.VoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandMatcherTest {
    private val attack = VoiceCommand("attack", "attack")
    private val nextPage = VoiceCommand("next_page", "next page")
    private val commands = StandardCommands.all + attack + nextPage

    @Test
    fun exactNeedsTheWholeUtterance() {
        assertEquals(attack, CommandMatcher.match("Attack!", commands, VoiceMatchMode.EXACT))
        assertNull(CommandMatcher.match("go attack now", commands, VoiceMatchMode.EXACT))
    }

    @Test
    fun anywhereFindsTheWordInASentence() {
        assertEquals(attack, CommandMatcher.match("go attack now", commands, VoiceMatchMode.WORD_ANYWHERE))
    }

    @Test
    fun anywhereMatchesWholeWordsOnly() {
        assertNull(CommandMatcher.match("counterattacking", listOf(attack), VoiceMatchMode.WORD_ANYWHERE))
    }

    @Test
    fun screenCommandsBeatGlobalOnes() {
        // "next page" (screen) and "next page" (global NEXT phrase) both match; the screen wins.
        assertEquals(nextPage, CommandMatcher.match("next page please", commands, VoiceMatchMode.WORD_ANYWHERE))
    }

    @Test
    fun longestPhraseWins() {
        val back = VoiceCommand("go_back_screen", "go back", scope = CommandScope.SCREEN)
        val go = VoiceCommand("go", "go", scope = CommandScope.SCREEN)
        assertEquals(back, CommandMatcher.match("go back", listOf(go, back), VoiceMatchMode.WORD_ANYWHERE))
    }

    @Test
    fun globalsStillWorkWithoutScreenCommands() {
        assertEquals(StandardCommands.BACK, CommandMatcher.match("Go back.", StandardCommands.all, VoiceMatchMode.EXACT))
        assertEquals(StandardCommands.HOME, CommandMatcher.match("take me home", StandardCommands.all, VoiceMatchMode.WORD_ANYWHERE))
    }

    @Test
    fun laterHypothesesAreTriedWhenTheFirstDoesNotMatch() {
        val result = CommandMatcher.match(listOf("a tack", "attack"), commands, VoiceMatchMode.EXACT)
        assertEquals(attack, result)
    }

    @Test
    fun normalizesCaseAndPunctuation() {
        assertEquals("dont stop me now", CommandMatcher.normalize("  Don't  STOP, me now! "))
    }

    @Test
    fun userShortcutsBecomeGlobalCommands() {
        val shortcuts = StandardCommands.shortcuts(mapOf(VoiceShortcut.JOYSTICK_MODE to "stick please", VoiceShortcut.CURSOR_MODE to ""))
        assertEquals(1, shortcuts.size)
        val matched = CommandMatcher.match("stick please", shortcuts, VoiceMatchMode.EXACT)!!
        assertEquals(VoiceShortcut.JOYSTICK_MODE, StandardCommands.shortcutOf(matched))
    }
}

class VoiceActivationGateTest {
    private val attack = VoiceCommand("attack", "attack")

    @Test
    fun immediateFiresOnThePartialAndNotAgainOnTheFinal() {
        val gate = VoiceActivationGate()
        assertEquals(attack, gate.offer(attack, isFinal = false, mode = VoiceActivationMode.IMMEDIATE))
        assertNull(gate.offer(attack, isFinal = false, mode = VoiceActivationMode.IMMEDIATE))
        assertNull(gate.offer(attack, isFinal = true, mode = VoiceActivationMode.IMMEDIATE))
        // A new utterance can fire it again.
        assertEquals(attack, gate.offer(attack, isFinal = false, mode = VoiceActivationMode.IMMEDIATE))
    }

    @Test
    fun immediateStillFiresIfOnlyTheFinalMatches() {
        val gate = VoiceActivationGate()
        assertNull(gate.offer(null, isFinal = false, mode = VoiceActivationMode.IMMEDIATE))
        assertEquals(attack, gate.offer(attack, isFinal = true, mode = VoiceActivationMode.IMMEDIATE))
    }

    @Test
    fun afterFinishWaitsForTheFinal() {
        val gate = VoiceActivationGate()
        assertNull(gate.offer(attack, isFinal = false, mode = VoiceActivationMode.AFTER_FINISH))
        assertEquals(attack, gate.offer(attack, isFinal = true, mode = VoiceActivationMode.AFTER_FINISH))
    }

    @Test
    fun afterFinishIgnoresAPartialMatchTheFinalDropped() {
        val gate = VoiceActivationGate()
        gate.offer(attack, isFinal = false, mode = VoiceActivationMode.AFTER_FINISH)
        assertNull(gate.offer(null, isFinal = true, mode = VoiceActivationMode.AFTER_FINISH))
    }
}
