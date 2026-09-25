package com.pwde.app.sensors

import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.sensors.face.CursorMapper
import com.pwde.app.sensors.face.CursorPosition
import com.pwde.app.sensors.face.HeadPose
import com.pwde.app.sensors.face.JoystickDirection
import com.pwde.app.sensors.face.JoystickMapper
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

    @Test
    fun insideDeadZoneIsCentered() {
        val state = JoystickMapper.map(HeadPose(0f, 0.5f, 0.5f), tuning.copy(deadZone = 10))
        assertEquals(JoystickDirection.CENTER, state.direction)
        assertEquals(0f, state.x, 0f)
        assertEquals(0f, state.y, 0f)
    }

    @Test
    fun rollSteersSidewaysAndPitchSteersVertically() {
        assertEquals(JoystickDirection.RIGHT, JoystickMapper.map(HeadPose(0f, 0f, 25f), tuning).direction)
        assertEquals(JoystickDirection.LEFT, JoystickMapper.map(HeadPose(0f, 0f, -25f), tuning).direction)
        assertEquals(JoystickDirection.UP, JoystickMapper.map(HeadPose(0f, 25f, 0f), tuning).direction)
        assertEquals(JoystickDirection.DOWN, JoystickMapper.map(HeadPose(0f, -25f, 0f), tuning).direction)
        assertEquals(JoystickDirection.UP_RIGHT, JoystickMapper.map(HeadPose(0f, 20f, 20f), tuning).direction)
    }

    @Test
    fun centerOffsetsTheNeutralPose() {
        val centered = tuning.copy(centerPitch = -10f, centerRoll = 5f)
        assertEquals(JoystickDirection.CENTER, JoystickMapper.map(HeadPose(0f, -10f, 5f), centered).direction)
    }

    @Test
    fun deflectionIsClampedToTheRim() {
        val state = JoystickMapper.map(HeadPose(0f, 0f, 90f), tuning)
        assertEquals(1f, state.x, 0.001f)
    }

    @Test
    fun higherSensitivityNeedsLessTilt() {
        val low = JoystickMapper.map(HeadPose(0f, 0f, 10f), tuning.copy(sensitivity = 1)).x
        val high = JoystickMapper.map(HeadPose(0f, 0f, 10f), tuning.copy(sensitivity = 10)).x
        assertTrue(high > low)
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

    @Test
    fun sizeSetsRadius() {
        assertTrue(JoystickMapper.map(HeadPose.NEUTRAL, tuning.copy(size = 10)).radius > JoystickMapper.map(HeadPose.NEUTRAL, tuning.copy(size = 1)).radius)
    }
}
