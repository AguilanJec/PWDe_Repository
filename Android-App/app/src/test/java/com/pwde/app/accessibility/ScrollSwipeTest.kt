package com.pwde.app.accessibility

import com.pwde.app.play.ScrollDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSwipeTest {
    @Test
    fun showingWhatIsBelowMovesTheFingerUp() {
        val travel = ScrollSwipe.travel(1200f, 540f, ScrollDirection.DOWN, 2400, 1080)
        assertTrue("down means the finger travels up the screen", travel.toY < travel.fromY)
        assertEquals(1200f, travel.fromX, TOLERANCE)
        assertEquals(1200f, travel.toX, TOLERANCE)
    }

    @Test
    fun eachDirectionTravelsTheWayItReads() {
        val up = ScrollSwipe.travel(1200f, 540f, ScrollDirection.UP, 2400, 1080)
        assertTrue(up.toY > up.fromY)
        val right = ScrollSwipe.travel(1200f, 540f, ScrollDirection.RIGHT, 2400, 1080)
        assertTrue(right.toX < right.fromX)
        val left = ScrollSwipe.travel(1200f, 540f, ScrollDirection.LEFT, 2400, 1080)
        assertTrue(left.toX > left.fromX)
    }

    @Test
    fun aTravelFromTheScreenEdgeStaysOnScreen() {
        val travel = ScrollSwipe.travel(1f, 1079f, ScrollDirection.DOWN, 2400, 1080)
        assertTrue(travel.fromX >= 1f)
        assertTrue(travel.fromY >= 1f)
        assertTrue(travel.toX <= 2398f)
        assertTrue(travel.toY <= 1078f)
    }

    /** The finger has to keep moving for the whole travel, so the game reads it as a swipe, not a tap. */
    @Test
    fun theFingerReachesTheEndAndStaysThere() {
        val travel = ScrollSwipe.travel(1200f, 540f, ScrollDirection.DOWN, 2400, 1080)
        val (startX, startY) = ScrollSwipe.pointAt(travel, 0, 300)
        assertEquals(travel.fromX, startX, TOLERANCE)
        assertEquals(travel.fromY, startY, TOLERANCE)
        val (endX, endY) = ScrollSwipe.pointAt(travel, 300, 300)
        assertEquals(travel.toX, endX, TOLERANCE)
        assertEquals(travel.toY, endY, TOLERANCE)
        val (pastX, pastY) = ScrollSwipe.pointAt(travel, 900, 300)
        assertEquals("past the end it must not keep going", travel.toX, pastX, TOLERANCE)
        assertEquals(travel.toY, pastY, TOLERANCE)
        val (halfX, _) = ScrollSwipe.pointAt(travel, 150, 300)
        assertEquals((travel.fromX + travel.toX) / 2, halfX, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
