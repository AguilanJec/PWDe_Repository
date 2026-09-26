package com.pwde.app.accessibility

import com.pwde.app.accessibility.JoystickGestureMachine.Kind
import com.pwde.app.accessibility.JoystickGestureMachine.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stick's stroke machine, ported with the reference implementation's own tests. They guard the
 * behaviour the jitter reports were about: a deflected stick is ONE continuous press/drag/hold/release
 * rather than a stream of short strokes, and movement smaller than `minMovePx` never reaches the game.
 */
class JoystickGestureMachineTest {
    private fun machine() = JoystickGestureMachine(RELEASE_GRACE_MS, MIN_INTERVAL_MS)

    private fun update(
        machine: JoystickGestureMachine,
        nowMs: Long,
        deflected: Boolean,
        targetX: Float,
        targetY: Float,
    ): Step? = machine.update(nowMs, deflected, BASE_X, BASE_Y, targetX, targetY, MIN_MOVE_PX)

    @Test
    fun whileIdleNothingIsDispatched() {
        val machine = machine()
        repeat(5) { i -> assertNull(update(machine, NOW + i * 16L, false, BASE_X, BASE_Y)) }
        assertFalse(machine.isPressed())
    }

    @Test
    fun theOnsetPressesAtTheBaseAndDragsToTheDeflection() {
        val machine = machine()
        val step = update(machine, NOW, true, TARGET_X, TARGET_Y)
        assertNotNull(step)
        assertEquals(Kind.PRESS_DRAG, step!!.kind)
        assertEquals(BASE_X, step.fromX, TOLERANCE)
        assertEquals(BASE_Y, step.fromY, TOLERANCE)
        assertEquals(TARGET_X, step.toX, TOLERANCE)
        assertEquals(TARGET_Y, step.toY, TOLERANCE)
        assertTrue(machine.isPressed())
    }

    /** The core anti-jitter rule: a held deflection must not produce a stroke on every frame. */
    @Test
    fun holdingStillDispatchesNothing() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        for (i in 1..30) {
            assertNull("no stroke expected while holding still", update(machine, NOW + i * 16L, true, TARGET_X, TARGET_Y))
        }
        assertTrue(machine.isPressed())
    }

    /** Tremor smaller than minMovePx must never move the finger, which is stronger than damping. */
    @Test
    fun jitterWithinTheMoveStepDispatchesNothing() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        for (i in 1..10) {
            assertNull(update(machine, NOW + i * 16L, true, TARGET_X + 2f, TARGET_Y - 2f))
        }
    }

    @Test
    fun aRealMoveDispatchesADragFromWhereTheFingerIs() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        val newX = TARGET_X + 60f
        val step = update(machine, NOW + 40, true, newX, TARGET_Y)
        assertNotNull(step)
        assertEquals(Kind.DRAG, step!!.kind)
        assertEquals(TARGET_X, step.fromX, TOLERANCE)
        assertEquals(TARGET_Y, step.fromY, TOLERANCE)
        assertEquals(newX, step.toX, TOLERANCE)
    }

    @Test
    fun fastChangesAreRateLimitedToTheLatestTarget() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        // Inside the 30 ms interval the move is throttled...
        assertNull(update(machine, NOW + 10, true, TARGET_X + 60f, TARGET_Y))
        // ...then the drag catches up to the latest target.
        val first = update(machine, NOW + 40, true, TARGET_X + 60f, TARGET_Y)
        assertNotNull(first)
        assertEquals(TARGET_X, first!!.fromX, TOLERANCE)
        assertEquals(TARGET_X + 60f, first.toX, TOLERANCE)
        // A second big move is throttled again, then caught up on the next tick.
        assertNull(update(machine, NOW + 50, true, TARGET_X + 120f, TARGET_Y))
        val second = update(machine, NOW + 80, true, TARGET_X + 120f, TARGET_Y)
        assertNotNull(second)
        assertEquals(TARGET_X + 60f, second!!.fromX, TOLERANCE)
        assertEquals(TARGET_X + 120f, second.toX, TOLERANCE)
    }

    @Test
    fun releasingWaitsForTheGraceWindow() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        // Deflection lost at NOW+100; inside the 150 ms grace the finger is still held.
        assertNull(update(machine, NOW + 100, false, BASE_X, BASE_Y))
        assertTrue("still held inside the release grace", machine.isPressed())
        assertNull(update(machine, NOW + 200, false, BASE_X, BASE_Y))
        // After the grace expires the finger lifts exactly where it is held.
        val release = update(machine, NOW + 260, false, BASE_X, BASE_Y)
        assertNotNull(release)
        assertEquals(Kind.RELEASE, release!!.kind)
        assertEquals(TARGET_X, release.toX, TOLERANCE)
        assertEquals(TARGET_Y, release.toY, TOLERANCE)
        assertFalse(machine.isPressed())
        // And it stays idle afterwards.
        assertNull(update(machine, NOW + 300, false, BASE_X, BASE_Y))
    }

    /** A momentary dip into the dead zone must not drop the stick. */
    @Test
    fun aDipInTheDeadZoneDoesNotReleaseTheHold() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        assertNull(update(machine, NOW + 20, false, BASE_X, BASE_Y))
        assertTrue(machine.isPressed())
        val resume = update(machine, NOW + 40, true, TARGET_X + 80f, TARGET_Y)
        assertNotNull(resume)
        assertTrue(machine.isPressed())
    }

    @Test
    fun forceReleaseLiftsImmediatelyAndStaysIdle() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        val release = machine.forceRelease()
        assertNotNull(release)
        assertEquals(Kind.RELEASE, release!!.kind)
        assertFalse(machine.isPressed())
        // A second one is a no-op, and a later deflection starts a fresh press.
        assertNull(machine.forceRelease())
        val press = update(machine, NOW + 50, true, TARGET_X, TARGET_Y)
        assertNotNull(press)
        assertEquals(Kind.PRESS_DRAG, press!!.kind)
    }

    @Test
    fun resetDropsTheHoldWithoutReleasing() {
        val machine = machine()
        update(machine, NOW, true, TARGET_X, TARGET_Y)
        machine.reset()
        assertFalse(machine.isPressed())
        assertNull(update(machine, NOW + 100, false, BASE_X, BASE_Y))
    }

    /** End to end: one press, a long hold, one release — and nothing in between. */
    @Test
    fun aWholePushIsExactlyOnePressAndOneRelease() {
        val machine = machine()
        val press = update(machine, NOW, true, TARGET_X, TARGET_Y)
        assertEquals(Kind.PRESS_DRAG, press!!.kind)
        for (i in 1..100) {
            assertNull(update(machine, NOW + i * 16L, true, TARGET_X, TARGET_Y))
        }
        // The grace starts when the deflection is first lost, then the release follows.
        assertNull(update(machine, NOW + 1700, false, BASE_X, BASE_Y))
        val release = update(machine, NOW + 1900, false, BASE_X, BASE_Y)
        assertNotNull(release)
        assertEquals(Kind.RELEASE, release!!.kind)
        assertFalse(machine.isPressed())
    }

    private companion object {
        const val NOW = 1_000_000L
        const val BASE_X = 100f
        const val BASE_Y = 800f
        const val TARGET_X = 150f
        const val TARGET_Y = 760f
        const val MIN_MOVE_PX = 10f
        const val RELEASE_GRACE_MS = 150f
        const val MIN_INTERVAL_MS = 30L
        const val TOLERANCE = 0.001f
    }
}
