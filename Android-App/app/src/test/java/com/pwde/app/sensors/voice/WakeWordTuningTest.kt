package com.pwde.app.sensors.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The hand-editable half of the wake word tuning: what actually reaches the keywords file, and how a
 * value stepped or typed out of range is pulled back into what the native spotter accepts.
 */
class WakeWordTuningTest {

    @Test
    fun `suffix writes both numbers or nothing`() {
        assertEquals(" :7.5 #0.5", WakeWordTuning.suffix(WakeWordTuning(7.5f, 0.5f)))
        // Either number missing means the line falls back to the spotter's pair, not half of it: the
        // native parser reads `:boost #threshold` as a unit.
        assertEquals("", WakeWordTuning.suffix(WakeWordTuning(7.5f, null)))
        assertEquals("", WakeWordTuning.suffix(WakeWordTuning(null, 0.5f)))
        assertEquals("", WakeWordTuning.suffix(WakeWordTuning.INHERIT))
        assertEquals("", WakeWordTuning.suffix(null))
    }

    @Test
    fun `a hand-edited pair matches no preset`() {
        assertEquals(WakeWordSensitivity.MAX, WakeWordSensitivity.presetFor(WakeWordTuning(4.5f, 0.05f)))
        assertEquals(WakeWordSensitivity.NORMAL, WakeWordSensitivity.presetFor(WakeWordTuning.INHERIT))
        // The toggle must show nothing selected rather than highlight the wrong preset.
        assertNull(WakeWordSensitivity.presetFor(WakeWordTuning(4.6f, 0.05f)))
    }

    @Test
    fun `clamping pulls every field into the range the native spotter accepts`() {
        val clamped =
            WakeWordSpotterTuning(score = -1f, threshold = 2f, trailingBlanks = -5, activePaths = 0).clamped()

        assertEquals(0f, clamped.score, 0f)
        assertEquals(1f, clamped.threshold, 0f)
        assertEquals(0, clamped.trailingBlanks)
        assertEquals(1, clamped.activePaths)
        // The shipped defaults must already be in range, or clamping would silently change them.
        assertEquals(WakeWordSpotterTuning(), WakeWordSpotterTuning().clamped())
    }

    @Test
    fun `stepping stays on the grid and comes back to where it started`() {
        val range = WakeWordSpotterTuning.THRESHOLD_RANGE
        var threshold = 0f
        repeat(3) { threshold = stepTuningValue(threshold, 0.05f, 1, range) }
        // Three taps must read exactly 0.15, not 0.15000001 in the keywords file.
        assertEquals(0.15f, threshold, 0f)

        repeat(3) { threshold = stepTuningValue(threshold, 0.05f, -1, range) }
        assertEquals(0f, threshold, 0f)

        // Stepping back onto a preset's numbers must restore it, so the toggle lights up again.
        val max = WakeWordSensitivity.MAX.tuning
        val up = stepTuningValue(max.boost!!, 0.5f, 1, WakeWordSpotterTuning.SCORE_RANGE)
        val down = stepTuningValue(up, 0.5f, -1, WakeWordSpotterTuning.SCORE_RANGE)
        assertEquals(WakeWordSensitivity.MAX, WakeWordSensitivity.presetFor(max.copy(boost = down)))
        // And the range ends are hard stops rather than wrap-arounds.
        assertEquals(1f, stepTuningValue(1f, 0.05f, 1, range), 0f)
        assertEquals(0f, stepTuningValue(0f, 0.05f, -1, range), 0f)
    }
}
