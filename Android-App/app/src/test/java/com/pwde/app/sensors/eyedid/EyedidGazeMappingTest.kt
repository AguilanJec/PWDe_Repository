package com.pwde.app.sensors.eyedid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gaze maths and the pointer's hold/smooth behaviour, tested without a device. These are the
 * parts that decide whether the dot looks right, and they are also the parts a camera makes hard to
 * reason about by hand.
 */
class EyedidGazeMappingTest {

    // ---- screen pixels -> box fraction ------------------------------------------------------------------

    @Test
    fun `a pixel position becomes a fraction of the box`() {
        val fraction = EyedidGazeMapping.toBoxFraction(x = 500f, y = 400f, boxLeftPx = 0f, boxTopPx = 0f, boxWidthPx = 1000, boxHeightPx = 800)

        assertEquals(0.5f, fraction!!.x, 1e-4f)
        assertEquals(0.5f, fraction.y, 1e-4f)
    }

    @Test
    fun `the box's own offset on the screen is subtracted`() {
        // A box from (100, 200) to (300, 400) is 200x200; its centre is the screen point (200, 300).
        val fraction = EyedidGazeMapping.toBoxFraction(x = 200f, y = 300f, boxLeftPx = 100f, boxTopPx = 200f, boxWidthPx = 200, boxHeightPx = 200)

        assertEquals(0.5f, fraction!!.x, 1e-4f)
        assertEquals(0.5f, fraction.y, 1e-4f)
    }

    @Test
    fun `an unmeasured box yields no point`() {
        // Dividing by a zero width would produce NaN, which draws nowhere and looks like a bug.
        assertNull(EyedidGazeMapping.toBoxFraction(10f, 10f, 0f, 0f, 0, 500))
        assertNull(EyedidGazeMapping.toBoxFraction(10f, 10f, 0f, 0f, 500, 0))
    }

    @Test
    fun `a non-finite reading yields no point`() {
        assertNull(EyedidGazeMapping.toBoxFraction(Float.NaN, 10f, 0f, 0f, 500, 500))
        assertNull(EyedidGazeMapping.toBoxFraction(10f, Float.POSITIVE_INFINITY, 0f, 0f, 500, 500))
    }

    @Test
    fun `looking past an edge pins the point to that edge instead of dropping it`() {
        // The SDK reports off-screen points when the user looks at the bezel; dropping them makes the
        // dot flicker exactly where it is most obvious.
        assertEquals(0f, EyedidGazeMapping.toBoxFraction(-50f, 10f, 0f, 0f, 500, 500)!!.x, 1e-4f)
        assertEquals(1f, EyedidGazeMapping.toBoxFraction(900f, 10f, 0f, 0f, 500, 500)!!.x, 1e-4f)
    }

    // ---- the pointer ------------------------------------------------------------------------------------

    private val centre = GazePoint(0.5f, 0.5f)

    @Test
    fun `the first reading is followed exactly so the pointer starts where the eyes are`() {
        val pointer = EyedidPointer()

        val point = pointer.update(GazePoint(0.2f, 0.8f), faceVisible = true, nowMs = 1_000L)

        assertEquals(0.2f, point!!.x, 1e-4f)
        assertEquals(0.8f, point.y, 1e-4f)
        assertNull(pointer.reason)
    }

    @Test
    fun `smoothing pulls the pointer toward the eyes rather than jumping to them`() {
        val pointer = EyedidPointer(smoothing = 0.25f)
        pointer.update(GazePoint(0f, 0f), faceVisible = true, nowMs = 0L)

        val point = pointer.update(GazePoint(1f, 1f), faceVisible = true, nowMs = 16L)

        // A quarter of the way there: moved, but not immediately on target.
        assertEquals(0.25f, point!!.x, 1e-4f)
    }

    @Test
    fun `a blink does not release the pointer`() {
        val pointer = EyedidPointer(holdMs = 400L)
        pointer.update(centre, faceVisible = true, nowMs = 0L)

        // Eyes closed for a few frames: the face is still there, so the dot holds.
        val held = pointer.update(reading = null, faceVisible = true, nowMs = 100L)

        assertNotNull(held)
        assertEquals(centre.x, held!!.x, 1e-4f)
        assertNull("holding is not a loss", pointer.reason)
    }

    @Test
    fun `looking away long enough does release the pointer`() {
        val pointer = EyedidPointer(holdMs = 400L)
        pointer.update(centre, faceVisible = true, nowMs = 0L)

        assertNull(pointer.update(null, faceVisible = true, nowMs = 900L))
        assertEquals(GazeLostReason.EYES_LOST, pointer.reason)
    }

    @Test
    fun `losing the face drops the pointer so it cannot glide from somewhere stale`() {
        val pointer = EyedidPointer()
        pointer.update(centre, faceVisible = true, nowMs = 0L)

        // No face is different from a blink: holding here would leave a dot with nothing behind it.
        assertNull(pointer.update(null, faceVisible = false, nowMs = 16L))
        assertEquals(GazeLostReason.NO_FACE, pointer.reason)
    }

    @Test
    fun `a pointer that was never placed is never held`() {
        val pointer = EyedidPointer()
        pointer.trackingStarted()

        // A blink before the first reading has nothing to hold on to.
        assertNull(pointer.update(null, faceVisible = true, nowMs = 5L))
    }

    @Test
    fun `the pointer restarts from where the eyes are after a loss`() {
        val pointer = EyedidPointer()
        pointer.update(GazePoint(0.1f, 0.1f), faceVisible = true, nowMs = 0L)
        pointer.update(null, faceVisible = false, nowMs = 16L)

        // Re-acquired: snapping is right, easing would drag the dot in from the old position.
        val point = pointer.update(GazePoint(0.9f, 0.9f), faceVisible = true, nowMs = 32L)

        assertEquals(0.9f, point!!.x, 1e-4f)
    }

    @Test
    fun `stopping tracking clears the point and says why`() {
        val pointer = EyedidPointer()
        pointer.update(centre, faceVisible = true, nowMs = 0L)

        pointer.trackingStopped()

        assertNull(pointer.point)
        assertEquals(GazeLostReason.NOT_TRACKING, pointer.reason)
    }

    @Test
    fun `starting tracking draws nothing until the first reading`() {
        val pointer = EyedidPointer()

        pointer.trackingStarted()

        assertNull(pointer.point)
        assertEquals(GazeLostReason.NO_FACE, pointer.reason)
    }
}
