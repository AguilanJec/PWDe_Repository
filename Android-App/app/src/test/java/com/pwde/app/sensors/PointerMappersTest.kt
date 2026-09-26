package com.pwde.app.sensors

import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.sensors.face.CursorMapper
import com.pwde.app.sensors.face.CursorPosition
import com.pwde.app.sensors.face.HeadPose
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.face.JoystickMapper
import com.pwde.app.sensors.face.JoystickTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorMapperTest {
    /** No smoothing lag in these tests: level 1 ≈ follow almost immediately. */
    private val fast = CursorTuning(smoothing = 1)

    private fun CursorMapper.turn(fromYaw: Float, toYaw: Float, pitch: Float = 0f, tuning: CursorTuning = fast, steps: Int = 20): CursorPosition {
        var p = CursorPosition.CENTER
        for (i in 0..steps) p = update(HeadPose(fromYaw + (toYaw - fromYaw) * i / steps, pitch, 0f), tuning)
        repeat(10) { p = update(HeadPose(toYaw, pitch, 0f), tuning) }
        return p
    }

    @Test
    fun turningRightMovesRight() {
        val p = CursorMapper().turn(0f, 10f)
        assertTrue(p.x > 0.5f)
        assertEquals(0.5f, p.y, 0.001f)
    }

    @Test
    fun lookingUpMovesUp() {
        val mapper = CursorMapper()
        var p = CursorPosition.CENTER
        for (i in 0..20) p = mapper.update(HeadPose(0f, i * 0.5f, 0f), fast)
        assertTrue(p.y < 0.5f)
    }

    @Test
    fun perDirectionSpeedsAreIndependent() {
        val slowLeft = fast.copy(speedLeft = 1, speedRight = 10)
        val right = CursorMapper().turn(0f, 5f, tuning = slowLeft).x - 0.5f
        val left = 0.5f - CursorMapper().turn(0f, -5f, tuning = slowLeft).x
        assertTrue("right $right should outpace left $left", right > left * 5)
    }

    @Test
    fun staysOnScreen() {
        val p = CursorMapper().turn(0f, 200f, tuning = fast.copy(speedRight = 10))
        assertEquals(1f, p.x, 0f)
    }

    @Test
    fun recenterReturnsToMiddle() {
        val mapper = CursorMapper()
        mapper.turn(0f, 10f)
        mapper.recenter()
        val p = mapper.update(HeadPose(10f, 0f, 0f), fast)
        assertEquals(0.5f, p.x, 0.01f)
    }

    @Test
    fun gainGrowsWithLevel() {
        assertTrue(CursorMapper.gain(10) > CursorMapper.gain(5))
        assertTrue(CursorMapper.gain(5) > CursorMapper.gain(1))
    }
}

class JoystickMapperTest {
    private val tuning = JoystickTuning(size = 5, sensitivity = 5, deadZone = 1)

    /** One reading from a freshly engaged stick, with no smoothing lag. */
    private fun stick(pose: HeadPose, tuning: JoystickTuning = this.tuning) =
        JoystickTracker().update(pose, tuning, smoothing = 1)

    @Test
    fun insideDeadZoneIsCentered() {
        val state = stick(HeadPose(0f, 0.5f, 0.5f), tuning.copy(deadZone = 10))
        assertEquals(JoystickDirection.CENTER, state.direction)
        assertEquals(0f, state.x, 0f)
        assertEquals(0f, state.y, 0f)
    }

    @Test
    fun rollSteersSidewaysAndPitchSteersVertically() {
        assertEquals(JoystickDirection.RIGHT, stick(HeadPose(0f, 0f, 25f)).direction)
        assertEquals(JoystickDirection.LEFT, stick(HeadPose(0f, 0f, -25f)).direction)
        assertEquals(JoystickDirection.UP, stick(HeadPose(0f, 25f, 0f)).direction)
        assertEquals(JoystickDirection.DOWN, stick(HeadPose(0f, -25f, 0f)).direction)
        assertEquals(JoystickDirection.UP_RIGHT, stick(HeadPose(0f, 20f, 20f)).direction)
    }

    @Test
    fun centerOffsetsTheNeutralPose() {
        val centered = tuning.copy(centerPitch = -10f, centerRoll = 5f)
        assertEquals(JoystickDirection.CENTER, stick(HeadPose(0f, -10f, 5f), centered).direction)
    }

    @Test
    fun deflectionIsClampedToTheRim() {
        assertEquals(1f, stick(HeadPose(0f, 0f, 90f)).x, 0.001f)
    }

    @Test
    fun higherSensitivityNeedsLessTilt() {
        val low = stick(HeadPose(0f, 0f, 10f), tuning.copy(sensitivity = 1)).x
        val high = stick(HeadPose(0f, 0f, 10f), tuning.copy(sensitivity = 10)).x
        assertTrue(high > low)
    }

    /**
     * The old full-scale curve needed a 23° head tilt at the default level, which is a strain rather
     * than a steering gesture — the "barely responds, needs a huge tilt" report.
     */
    @Test
    fun theDefaultSensitivityReachesTheRimWithoutStraining() {
        assertEquals(1f, stick(HeadPose(0f, 0f, 14f)).x, 0.001f)
    }

    /**
     * The dead zone used to be a share of the full scale, so raising Sensibility silently shrank it
     * and the same tilt drifted at one level but not the other.
     */
    @Test
    fun deadZoneDoesNotMoveWhenSensitivityChanges() {
        val quiet = tuning.copy(sensitivity = 1, deadZone = 5)
        assertEquals(JoystickDirection.CENTER, stick(HeadPose(0f, 0f, 3f), quiet).direction)
        assertEquals(JoystickDirection.CENTER, stick(HeadPose(0f, 0f, 3f), quiet.copy(sensitivity = 10)).direction)
    }

    /**
     * A head resting on the dead-zone edge used to flip CENTER/direction every frame, and each flip
     * makes the accessibility layer lift and re-press the held finger — the game sees taps instead
     * of a held stick, i.e. "twitchy".
     */
    @Test
    fun leavingAndReturningToTheDeadZoneUseDifferentThresholds() {
        val hysteresis = tuning.copy(deadZone = 4)   // 3° to leave, 1.8° to come back
        val tracker = JoystickTracker()
        assertEquals(JoystickDirection.CENTER, tracker.update(HeadPose(0f, 0f, 0f), hysteresis, 1).direction)
        assertEquals(JoystickDirection.RIGHT, tracker.update(HeadPose(0f, 0f, 6f), hysteresis, 1).direction)
        assertEquals(JoystickDirection.RIGHT, tracker.update(HeadPose(0f, 0f, 2f), hysteresis, 1).direction)
        assertEquals(JoystickDirection.CENTER, tracker.update(HeadPose(0f, 0f, 1f), hysteresis, 1).direction)
    }

    @Test
    fun smoothingDampsASpikeInsteadOfPassingItStraightThrough() {
        val tracker = JoystickTracker()
        tracker.update(HeadPose(0f, 0f, 8f), tuning, HEAVY_SMOOTHING)
        val spiked = tracker.update(HeadPose(0f, 0f, 18f), tuning, HEAVY_SMOOTHING).x
        assertTrue(spiked < stick(HeadPose(0f, 0f, 18f)).x)
    }

    @Test
    fun eightWaySnapping() {
        assertEquals(JoystickDirection.RIGHT, JoystickMapper.directionOf(1f, 0.1f))
        assertEquals(JoystickDirection.DOWN_RIGHT, JoystickMapper.directionOf(1f, 1f))
        assertEquals(JoystickDirection.DOWN, JoystickMapper.directionOf(0f, 1f))
        assertEquals(JoystickDirection.DOWN_LEFT, JoystickMapper.directionOf(-1f, 1f))
        assertEquals(JoystickDirection.LEFT, JoystickMapper.directionOf(-1f, 0f))
        assertEquals(JoystickDirection.UP_LEFT, JoystickMapper.directionOf(-1f, -1f))
        assertEquals(JoystickDirection.UP, JoystickMapper.directionOf(0f, -1f))
        assertEquals(JoystickDirection.UP_RIGHT, JoystickMapper.directionOf(1f, -1f))
    }

    /** Level 5 keeps the travel the accessibility stepper used to hard-code, so nothing moves in game. */
    @Test
    fun sizeSetsTheSticksTravel() {
        assertTrue(JoystickMapper.radiusFor(10) > JoystickMapper.radiusFor(1))
        assertEquals(0.12f, JoystickMapper.radiusFor(5), 0.02f)
    }

    /** The dead-zone ring the UI draws is the degree threshold as a share of the full scale. */
    @Test
    fun theDrawnDeadZoneFollowsTheDegreeThreshold() {
        assertTrue(JoystickMapper.deadZoneFraction(tuning.copy(deadZone = 10)) > JoystickMapper.deadZoneFraction(tuning))
    }

    private companion object {
        /** Level 10 — heavy damping, so the lag is unmistakable. */
        const val HEAVY_SMOOTHING = 10
    }
}
