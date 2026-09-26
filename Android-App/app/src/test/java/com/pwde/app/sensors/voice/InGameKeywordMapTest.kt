package com.pwde.app.sensors.voice

import com.pwde.app.play.GameInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InGameKeywordMapTest {
    @Test
    fun everyPhraseMapsToItsCommand() {
        val map = inGameKeywordMap(listOf(VoiceCommandBinding("button:1", listOf("attack", "hit"))))
        assertEquals(mapOf("attack" to "button:1", "hit" to "button:1"), map)
    }

    @Test
    fun phrasesAreNormalizedTheWaySpeechIs() {
        val map = inGameKeywordMap(listOf(VoiceCommandBinding("button:1", listOf("  Don't  STOP! "))))
        assertEquals(mapOf("dont stop" to "button:1"), map)
    }

    @Test
    fun theFirstBindingKeepsASharedPhrase() {
        val map = inGameKeywordMap(
            listOf(
                VoiceCommandBinding(GameInput.PAUSE, listOf("pause")),
                VoiceCommandBinding("button:1", listOf("Pause", "skill")),
            ),
        )
        assertEquals(GameInput.PAUSE, map["pause"])
        assertEquals("button:1", map["skill"])
    }

    @Test
    fun blankPhrasesAreSkipped() {
        assertTrue(inGameKeywordMap(listOf(VoiceCommandBinding("button:1", listOf(" ", "!?")))).isEmpty())
    }

    @Test
    fun standardCommandsAndButtonTriggersComeThrough() {
        val map = inGameKeywordMap(GameInput.STANDARD_BINDINGS + VoiceCommandBinding("button:7", listOf("ultimate")))
        assertEquals(GameInput.BACK, map["go back"])
        assertEquals(GameInput.RECENTER, map["center joystick"])
        assertEquals("button:7", map["ultimate"])
    }
}
