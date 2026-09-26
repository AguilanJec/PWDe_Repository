package com.pwde.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sequencing rules of the touch chain. Both device failures of 2026-09-26 were rule violations, so
 * they are pinned here: a gesture that lifts one finger while pressing another is refused by the
 * framework, and a queued finger that isn't counted as work stops the chain for good.
 */
class TouchPlanTest {
    private val stick = TouchPlan.Down("stick", releasing = false, expired = false)
    private val tap = "tap-0"

    /** The refused gesture: the stick letting go and the button going down in the same dispatch. */
    @Test
    fun aFingerNeverStartsInTheSegmentThatLiftsAnother() {
        val plan = TouchPlan.forSegment(listOf(stick.copy(releasing = true)), listOf(tap), maxFingers = 10)
        assertEquals(emptyList<String>(), plan.start)
        assertEquals(listOf("stick"), plan.lifted)
        assertTrue(plan.continued.isEmpty())
    }

    /** So the press goes down on its own, in the segment after the lift. */
    @Test
    fun thePressGetsASegmentOfItsOwnOnceTheStickIsUp() {
        val plan = TouchPlan.forSegment(emptyList(), listOf(tap), maxFingers = 10)
        assertEquals(listOf(tap), plan.start)
        assertTrue(plan.lifted.isEmpty())
        assertTrue(plan.continued.isEmpty())
    }

    /** Nothing new may share the screen with the held joystick — the game ignores such a tap. */
    @Test
    fun aHeldFingerKeepsGoingWhileNothingStarts() {
        val plan = TouchPlan.forSegment(listOf(stick), listOf(tap), maxFingers = 10)
        assertEquals(listOf("stick"), plan.continued)
        assertEquals(emptyList<String>(), plan.start)
    }

    /** A finger that ran out of time or was released is lifted, not continued. */
    @Test
    fun fingersthatAreDoneAreLiftedAndTheRestContinue() {
        val other = TouchPlan.Down("swipe", releasing = false, expired = true)
        val plan = TouchPlan.forSegment(listOf(stick, other), emptyList(), maxFingers = 10)
        assertEquals(listOf("stick"), plan.continued)
        assertEquals(listOf("swipe"), plan.lifted)
    }

    @Test
    fun noMoreFingersStartThanOneGestureCanHold() {
        val plan = TouchPlan.forSegment(emptyList(), listOf("a", "b", "c"), maxFingers = 2)
        assertEquals(listOf("a", "b"), plan.start)
    }

    /** The bug that stopped every gesture: a queued finger was not counted as work. */
    @Test
    fun aQueuedFingerIsWorkEvenWithNothingDown() {
        assertTrue(TouchPlan.hasWork(emptyList(), listOf(tap)))
        assertTrue(TouchPlan.hasWork(listOf(stick), emptyList()))
        assertFalse(TouchPlan.hasWork(emptyList(), emptyList()))
    }
}
