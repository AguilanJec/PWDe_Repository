package com.pwde.app.sensors.eyedid

import com.pwde.app.sensors.face.GazeDwell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a resting gaze becomes a press. Worth testing without a camera because every rule here is a
 * rule about time, and time is exactly what a real face makes impossible to reason about by hand.
 */
class EyedidDwellTrackerTest {

    private val holdMs = 800L
    private val here = GazePoint(0.5f, 0.5f)

    private fun tracker() = EyedidDwellTracker(holdMs = holdMs)

    @Test
    fun `resting long enough fires once`() {
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)

        assertNull("not yet", tracker.frame(here, held = here, nowMs = 400L))
        assertEquals(0.5f, tracker.progress, 1e-3f)

        val dwell = tracker.frame(here, held = here, nowMs = holdMs)

        assertNotNull(dwell)
        assertEquals(here.x, dwell!!.x, 1e-4f)
        assertEquals(here.y, dwell.y, 1e-4f)
    }

    @Test
    fun `it does not fire again while the gaze stays put`() {
        // Firing on "progress is full" every frame would press the button sixty times a second.
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)
        assertNotNull(tracker.frame(here, held = here, nowMs = holdMs))

        assertNull(tracker.frame(here, held = here, nowMs = holdMs + 16L))
        assertNull(tracker.frame(here, held = here, nowMs = holdMs + 32L))
        // The ring stays full so the user can see they are still on the same target.
        assertEquals(1f, tracker.progress, 1e-3f)
    }

    @Test
    fun `a blink does not restart a half-finished hold`() {
        // People blink constantly. Restarting the hold on every blink would mean a press could
        // almost never complete.
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)
        assertNull(tracker.frame(here, held = here, nowMs = 700L))
        assertEquals(0.875f, tracker.progress, 1e-3f)

        // Eyes shut for 300 ms: no reading, but the pointer is still holding its last point.
        assertNull(tracker.frame(reading = null, held = here, nowMs = 760L))
        assertNull(tracker.frame(reading = null, held = here, nowMs = 900L))
        assertEquals("the ring holds", 0.875f, tracker.progress, 1e-3f)

        // Eyes open again, past the original hold: the press is the user's, not the blink's.
        assertNotNull(tracker.frame(here, held = here, nowMs = 1_000L))
    }

    @Test
    fun `a blink alone never presses`() {
        // A held point is only returned through a short loss, so this cannot become "press with the
        // eyes shut" however long it goes on.
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)

        var fired: GazeDwell? = null
        for (now in 100L..3_000L step 100L) {
            fired = fired ?: tracker.frame(reading = null, held = here, nowMs = now)
        }

        assertNull(fired)
    }

    @Test
    fun `looking away and back presses again`() {
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)
        assertNotNull(tracker.frame(here, held = here, nowMs = holdMs))

        // Off the target, well outside the radius: this is a new visit.
        val away = GazePoint(0.9f, 0.9f)
        tracker.frame(away, held = away, nowMs = holdMs + 500L)
        tracker.frame(away, held = away, nowMs = holdMs + 1_000L)

        // Back on it, with the re-arm window long past.
        assertNull(tracker.frame(here, held = here, nowMs = 2_000L))
        assertNotNull(tracker.frame(here, held = here, nowMs = 2_000L + holdMs))
    }

    @Test
    fun `a gaze that will not settle never fires`() {
        // Merely looking around must not press anything, however long the user looks around for.
        val tracker = tracker()
        var now = 0L
        var fired: GazeDwell? = null
        repeat(40) {
            now += 100L
            // A slow sweep across the screen: always moving, never resting.
            val at = GazePoint(0.1f + it * 0.02f, 0.5f)
            fired = fired ?: tracker.frame(at, held = at, nowMs = now)
        }
        assertNull(fired)
    }

    @Test
    fun `drifting off the target restarts the wait`() {
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)
        tracker.frame(here, held = here, nowMs = 700L)

        // Just outside the default radius, so this counts as somewhere new.
        val away = GazePoint(here.x + EyedidDwellTracker.DEFAULT_RADIUS + 0.01f, here.y)
        assertNull(tracker.frame(away, held = away, nowMs = 750L))
        assertEquals(0f, tracker.progress, 1e-3f)

        // The original wait is gone, not merely paused.
        assertNull(tracker.frame(away, held = away, nowMs = 800L))
        assertNotNull(tracker.frame(away, held = away, nowMs = 750L + holdMs))
    }

    @Test
    fun `a lost gaze cannot be resting on anything`() {
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)
        tracker.frame(here, held = here, nowMs = 700L)

        // Nothing readable and nothing held: the face went, or the eyes were unreadable past the
        // hold window. Either way nothing is resting anywhere.
        assertNull(tracker.frame(reading = null, held = null, nowMs = 750L))
        assertNull(tracker.target)
        assertEquals(0f, tracker.progress, 1e-3f)

        // The gaze comes back, but the count starts again rather than resuming.
        assertNull(tracker.frame(here, held = here, nowMs = 800L))
        assertEquals(0f, tracker.progress, 1e-3f)
    }

    @Test
    fun `the ring starts filling at the spot the gaze settled on and stays there`() {
        // The ring marks the target. Following the drifting cursor would mean the user could not see
        // what they are about to press.
        val tracker = tracker()
        val start = GazePoint(0.30f, 0.30f)
        val drifted = GazePoint(0.32f, 0.32f)
        tracker.frame(start, held = start, nowMs = 0L)
        tracker.frame(drifted, held = drifted, nowMs = 400L)

        val target = tracker.target!!
        assertEquals(0.30f, target.x, 1e-4f)
        assertEquals(0.30f, target.y, 1e-4f)
    }

    @Test
    fun `a press too soon after the last one is held back until the re-arm window passes`() {
        // Sitting exactly on the radius boundary would otherwise release and re-arm on every wobble,
        // pressing twice for one look. A short hold makes the window the only thing deciding.
        val tracker = EyedidDwellTracker(holdMs = 100L, rearmMs = 250L)
        val off = GazePoint(here.x + EyedidDwellTracker.DEFAULT_RADIUS + 0.02f, here.y)

        tracker.frame(here, held = here, nowMs = 0L)
        assertNotNull("first press", tracker.frame(here, held = here, nowMs = 100L))

        // Wobble off and straight back; a full hold completes at 220 ms, only 120 ms after the press.
        tracker.frame(off, held = off, nowMs = 110L)
        tracker.frame(here, held = here, nowMs = 120L)
        assertNull("inside the re-arm window", tracker.frame(here, held = here, nowMs = 220L))

        // Still resting on it, with the window now passed.
        assertNotNull("window gone", tracker.frame(here, held = here, nowMs = 360L))
    }

    @Test
    fun `reset forgets a part-finished dwell`() {
        val tracker = tracker()
        tracker.frame(here, held = here, nowMs = 0L)
        tracker.frame(here, held = here, nowMs = 700L)

        tracker.reset()

        assertNull(tracker.target)
        assertEquals(0f, tracker.progress, 1e-3f)
    }
}
