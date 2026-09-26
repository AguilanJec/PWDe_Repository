package com.pwde.app.sensors.face

import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.DEFAULT_LEVEL
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
    val radius: Float = JoystickMapper.radiusFor(DEFAULT_LEVEL),
    /** The dead zone as a share of the full-scale tilt, so the UI can draw it. See [JoystickMapper]. */
    val deadZone: Float = JoystickMapper.deadZoneFraction(JoystickTuning()),
)

private fun levelFraction(level: Int) = (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL).toFloat() / (MAX_LEVEL - MIN_LEVEL)

/** Smoothing level 1 barely smooths; level 10 is heavy smoothing. Shared by the pointer and the stick. */
internal fun headSmoothingAlpha(level: Int): Float = 0.9f - 0.8f * levelFraction(level)

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
        fun smoothingAlpha(level: Int): Float = headSmoothingAlpha(level)
    }
}

/**
 * Head tilt joystick: roll steers left/right, pitch steers up/down, both from the user's center.
 *
 * The mapping follows ASPHI Nose-Drive (https://github.com/Fondazione-ASPHI/Nose-Drive,
 * `src/nosedrive/main.py`). Nose-Drive's whole mapping is: save a base pose once, then the input is
 * `(value - base) * sensitivity`, clamped to [-1, 1], per axis — and that clamped number *is* the
 * gamepad axis. It is absolute, not a step per frame, so the stick returns to the middle when the
 * head does, and sensitivity and the base stay independent knobs.
 *
 * Two departures, because this drives a real touch screen rather than a virtual gamepad:
 * - The dead zone is measured in **degrees**, not as a share of the full-scale tilt. Tying it to the
 *   full scale meant raising Sensibility silently shrank it (0.15 x 23° = 3.5° at level 5 but
 *   0.15 x 8° = 1.2° at level 10), so "ignores small head movements" quietly stopped being true.
 *   (Nose-Drive has no dead zone at all.)
 * - The dead zone has hysteresis. Without it a head resting near the boundary flips between CENTER
 *   and a direction every frame, and each flip makes the accessibility layer lift and re-press the
 *   finger — which the game sees as taps instead of a held stick.
 */
object JoystickMapper {
    /**
     * Head tilt for full deflection: 30° at level 1 down to 4° at level 10. Geometric, so a step
     * feels like a step, like [CursorMapper.gain]. The old linear 35°..8° curve needed 23° at the
     * default level, which is a strain rather than a steering gesture.
     */
    fun fullScaleDegrees(sensitivity: Int): Float =
        30f * SENSITIVITY_STEP.pow(sensitivity.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL)

    /** Head movement ignored around the base pose, in degrees. Independent of [fullScaleDegrees]. */
    fun deadZoneDegrees(level: Int): Float = 1f + 6f * levelFraction(level)

    /** The same dead zone as a share of the full-scale tilt, so the UI can draw it. */
    fun deadZoneFraction(tuning: JoystickTuning): Float =
        (deadZoneDegrees(tuning.deadZone) / fullScaleDegrees(tuning.sensitivity)).coerceIn(0f, 1f)

    /**
     * How far the game's stick is dragged at full deflection, as a share of the screen's short side.
     * Level 5 lands on the 0.12 the stepper used to hard-code, so this is the one source of truth for
     * the in-game drag, the button marker and the preview alike.
     */
    fun radiusFor(size: Int): Float = 0.06f + 0.12f * levelFraction(size)

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

    /** Per sensitivity level: 30° down to 4° across the nine steps. */
    private const val SENSITIVITY_STEP = 0.8f
}

/**
 * Live head joystick. Stateful, because it damps the deflection between frames and remembers whether
 * it is inside the dead zone; one instance per tracking session, like [CursorMapper].
 */
class JoystickTracker {
    private var x = 0f
    private var y = 0f
    private var engaged = false
    private var fresh = true

    fun update(pose: HeadPose, tuning: JoystickTuning, smoothing: Int): JoystickState {
        val radius = JoystickMapper.radiusFor(tuning.size)
        val deadZone = JoystickMapper.deadZoneFraction(tuning)
        val centered = JoystickState(0f, 0f, JoystickDirection.CENTER, radius, deadZone)

        val scale = JoystickMapper.fullScaleDegrees(tuning.sensitivity)
        val zone = JoystickMapper.deadZoneDegrees(tuning.deadZone)
        val dRoll = pose.roll - tuning.centerRoll
        // Looking up (pitch rising) pushes the stick up, i.e. toward y = 0.
        val dPitch = -(pose.pitch - tuning.centerPitch)

        // Gate in degrees, with hysteresis: leave past the dead zone, come back only well inside it.
        val tilt = sqrt(dRoll * dRoll + dPitch * dPitch)
        engaged = tilt > if (engaged) zone * RELEASE_FRACTION else zone
        if (!engaged) {
            x = 0f
            y = 0f
            fresh = true
            return centered
        }

        // Nose-Drive's mapping: deviation from the base over the full-scale tilt, clamped per axis.
        val span = (scale - zone).coerceAtLeast(1f)
        val reach = ((tilt - zone) / span).coerceIn(0f, 1f)
        val normalizedX = (dRoll / scale).coerceIn(-1f, 1f)
        val normalizedY = (dPitch / scale).coerceIn(-1f, 1f)
        val magnitude = sqrt(normalizedX * normalizedX + normalizedY * normalizedY)
        val targetX = if (magnitude == 0f) 0f else normalizedX / magnitude * reach
        val targetY = if (magnitude == 0f) 0f else normalizedY / magnitude * reach

        if (fresh) {
            // Start where the head is, so engaging the stick never sweeps in from the middle.
            x = targetX
            y = targetY
            fresh = false
        } else {
            val alpha = headSmoothingAlpha(smoothing)
            x += alpha * (targetX - x)
            y += alpha * (targetY - y)
        }
        return JoystickState(x, y, JoystickMapper.directionOf(x, y), radius, deadZone)
    }

    /** Forget the deflection, e.g. after the face was lost, so the stick doesn't sweep back in. */
    fun resetTracking() {
        engaged = false
        fresh = true
        x = 0f
        y = 0f
    }

    private companion object {
        /** The stick stays deflected until the head is this far back inside the dead zone. */
        const val RELEASE_FRACTION = 0.6f
    }
}
