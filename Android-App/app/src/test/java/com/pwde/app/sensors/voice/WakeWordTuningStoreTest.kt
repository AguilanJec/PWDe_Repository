package com.pwde.app.sensors.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class WakeWordTuningStoreTest {
    @Test
    fun anUntunedPhraseStartsOnTheMaxPreset() {
        assertEquals(WakeWordSensitivity.MAX.tuning, WakeWordTuningStore().tuningFor("attack"))
    }

    @Test
    fun tuningIsSharedAcrossSpellingsOfTheSamePhrase() {
        val store = WakeWordTuningStore()
        store.setPhraseTuning("Hey PWDE!", WakeWordSensitivity.HIGH.tuning)
        assertEquals(WakeWordSensitivity.HIGH.tuning, store.tuningFor("hey pwde"))
        assertEquals(mapOf("hey pwde" to WakeWordSensitivity.HIGH.tuning), store.tuningFor(listOf("hey pwde")))
    }

    @Test
    fun resetPutsEverythingBackOnTheDefaults() {
        val store = WakeWordTuningStore()
        store.setPhraseTuning("attack", WakeWordTuning.INHERIT)
        store.setSpotter(WakeWordSpotterTuning(score = 1f))
        store.reset()
        assertEquals(WakeWordSensitivity.MAX.tuning, store.tuningFor("attack"))
        assertEquals(WakeWordSpotterTuning(), store.spotter.value)
    }
}
