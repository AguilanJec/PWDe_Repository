package com.pwde.app.sensors.face

import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.MAX_LEVEL
import com.pwde.app.data.model.MIN_LEVEL
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

/** Pointer position, normalized: (0,0) top-left, (1,1) bottom-right. */
data class CursorPosition(val x: Float, val y: Float) {
    companion object {
        val CENTER = CursorPosition(0.5f, 0.5f)
    }
}

enum class JoystickDirection(val label: String) {
    CENTER("Center"), UP("Up"), UP_RIGHT("Up-right"), RIGHT("Right"), DOWN_RIGHT("Down-right"),
    DOWN("Down"), DOWN_LEFT("Down-left"), LEFT("Left"), UP_LEFT("Up-left"),
}

/**
 * Joystick deflection in screen convention: x > 0 right, y > 0 down, both in [-1, 1].
 * [radius] is the on-screen joystick size as a fraction of the shorter screen side.
 */
data class JoystickState(
    val x: Float = 0f,
    val y: Float = 0f,
    val direction: JoystickDirection = JoystickDirection.CENTER,
    val radius: Float = JoystickMapper.radiusFor(5),
    val deadZone: Float = JoystickMapper.deadZoneFor(3),
)

private fun levelFraction(level: Int) = (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL).toFloat() / (MAX_LEVEL - MIN_LEVEL)

/*
 * Relative head-pointer movement adapted from Google Project GameFace
 * (https://github.com/google/project-gameface, Apache License 2.0): the pointer moves by the change
 * in head angle times a per-direction speed, after exponential smoothing to calm tremor.
 */
class CursorMapper {
    private var position = CursorPosition.CENTER
    private var smoothYaw = Float.NaN
    private var smoothPitch = Float.NaN

    fun update(pose: HeadPose, tuning: CursorTuning): CursorPosition {
        val alpha = smoothingAlpha(tuning.smoothing)
        if (smoothYaw.isNaN()) {
            smoothYaw = pose.yaw
            smoothPitch = pose.pitch
            return position
        }
        val newYaw = smoothYaw + alpha * (pose.yaw - smoothYaw)
        val newPitch = smoothPitch + alpha * (pose.pitch - smoothPitch)
        val dYaw = newYaw - smoothYaw
        val dPitch = newPitch - smoothPitch
        smoothYaw = newYaw
        smoothPitch = newPitch

        val dx = if (abs(dYaw) < JITTER_DEGREES) 0f
        else dYaw * gain(if (dYaw > 0) tuning.speedRight else tuning.speedLeft)
        // Looking up (pitch rising) moves the pointer up, i.e. toward y = 0.
        val dy = if (abs(dPitch) < JITTER_DEGREES) 0f
        else -dPitch * gain(if (dPitch > 0) tuning.speedUp else tuning.speedDown)
        position = CursorPosition((position.x + dx).coerceIn(0f, 1f), (position.y + dy).coerceIn(0f, 1f))
        return position
    }

    fun recenter() {
        position = CursorPosition.CENTER
    }

    /** Forget the last head angle, e.g. after the face was lost, so the pointer doesn't jump. */
    fun resetTracking() {
        smoothYaw = Float.NaN
        smoothPitch = Float.NaN
    }

    companion object {
        /** Smoothed head movement below this per frame is treated as jitter. */
        const val JITTER_DEGREES = 0.02f

        /** Screen fraction per degree of head movement: level 1 is slow, level 10 is fast. */
        fun gain(level: Int): Float = 0.004f * 1.35f.pow(level.coerceIn(MIN_LEVEL, MAX_LEVEL) - 1)

        /** Smoothing level 1 barely smooths; level 10 is heavy smoothing. */
        fun smoothingAlpha(level: Int): Float = 0.9f - 0.8f * levelFraction(level)
    }
}

/** Head tilt joystick: roll steers left/right, pitch steers up/down, both from the user's center. */
object JoystickMapper {
    /** Degrees of tilt for full deflection: low sensitivity needs a big tilt. */
    fun fullScaleDegrees(sensitivity: Int): Float = 35f - 27f * levelFraction(sensitivity)

    fun deadZoneFor(level: Int): Float = 0.05f + 0.45f * levelFraction(level)

    fun radiusFor(size: Int): Float = 0.12f + 0.23f * levelFraction(size)

    fun map(pose: HeadPose, tuning: JoystickTuning): JoystickState {
        val scale = fullScaleDegrees(tuning.sensitivity)
        val rawX = ((pose.roll - tuning.centerRoll) / scale).coerceIn(-1f, 1f)
        val rawY = (-(pose.pitch - tuning.centerPitch) / scale).coerceIn(-1f, 1f)
        val deadZone = deadZoneFor(tuning.deadZone)
        val radius = radiusFor(tuning.size)
        val magnitude = sqrt(rawX * rawX + rawY * rawY)
        if (magnitude < deadZone) return JoystickState(0f, 0f, JoystickDirection.CENTER, radius, deadZone)

        // Rescale so movement starts from zero at the dead-zone edge, capped at the rim.
        val scaled = ((magnitude - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        val x = rawX / magnitude * scaled
        val y = rawY / magnitude * scaled
        return JoystickState(x, y, directionOf(rawX, rawY), radius, deadZone)
    }

    /** Snaps a vector (screen convention, y down) to one of 8 directions. */
    fun directionOf(x: Float, y: Float): JoystickDirection {
        if (x == 0f && y == 0f) return JoystickDirection.CENTER
        // Angle measured counter-clockwise from "right", with y flipped so up is positive.
        val degrees = (atan2(-y, x) * 180.0 / PI + 360.0) % 360.0
        return when (((degrees + 22.5) / 45.0).toInt() % 8) {
            0 -> JoystickDirection.RIGHT
            1 -> JoystickDirection.UP_RIGHT
            2 -> JoystickDirection.UP
            3 -> JoystickDirection.UP_LEFT
            4 -> JoystickDirection.LEFT
            5 -> JoystickDirection.DOWN_LEFT
            6 -> JoystickDirection.DOWN
            else -> JoystickDirection.DOWN_RIGHT
        }
    }
}
