package com.pwde.app.sensors.face

import kotlin.math.acos
import kotlin.math.sin

/**
 * Phone tilt, from the rotation sensor's 3x3 rotation matrix, as a [HeadPose] the joystick already
 * understands — so Size, Sensitivity, Dead zone and Smoothing keep the meaning they have for the head.
 *
 * Ported from the reference implementation's `GyroJoystickInput`, the version that behaves on a real
 * phone. Three of its choices are deliberate, and they are why it feels steady:
 *
 * 1. **The relative rotation is taken in the phone's own frame**, `transpose(neutral) · current`, so
 *    the control axes stay glued to the screen however the phone happens to be held.
 * 2. **It is read as an axis plus an angle, not as Euler angles.** A nod then moves the up/down axis
 *    and nothing else, and a roll moves the sideways axis and nothing else. An Euler decomposition
 *    couples the axes together, which is felt as the stick sliding sideways when the user only tips.
 * 3. **Only two of the three axes are used.** Rotation *about the screen axis* — twisting the phone
 *    within its own plane — is the least usable movement, and the reference ignores it outright.
 *    Steering the stick from that twist instead is why it wandered while the phone was held still.
 *
 * Matrices are 3x3 **column-major**, entry `(row, col)` at index `row + 3*col` — exactly as
 * `SensorManager.getRotationMatrixFromVector` lays one out.
 */
object GyroPoseMath {
    /**
     * How far the phone must tilt for full joystick travel: the reference's device-tested value. The
     * joystick scales from this for the gyro rather than from the head's much smaller full-scale tilt.
     */
    const val FULL_TILT_DEGREES = 40f

    /**
     * Deflection sign per axis: flip one to invert a direction. These are the *only* place a direction
     * is decided, so a device that feels mirrored is fixed here and nowhere else.
     *
     * [SIGN_X] is the reference implementation's value. **[SIGN_Y] was inverted on device testing**
     * (2026-09-26): the ported sign drove the stick backwards when the phone was tipped forward, so
     * the up/down axis is flipped from the reference here. [relativeDegrees] is unaffected — it is
     * the raw geometry, and the auto-recenter only uses its magnitude.
     */
    private const val SIGN_X = -1f
    private const val SIGN_Y = -1f

    /**
     * The signed rotation taking [neutral] to [current], in degrees about the neutral frame's three
     * axes: `[0]` nodding (the short, screen-horizontal axis), `[1]` rolling (the long axis), `[2]`
     * twisting (the screen axis, which the stick ignores).
     */
    fun relativeAxesDegrees(neutral: FloatArray, current: FloatArray): FloatArray {
        val rel = multiply(transpose(neutral), current)
        val angle = acos(((rel[0] + rel[4] + rel[8] - 1f) / 2f).coerceIn(-1f, 1f))
        val sinAngle = sin(angle)
        // No trustworthy axis this small: read it as no movement rather than dividing by ~zero.
        if (sinAngle < 1e-6f) return FloatArray(3)
        // Rodrigues' rotation axis, in the neutral phone frame.
        val axisX = (rel[5] - rel[7]) / (2f * sinAngle)
        val axisY = (rel[6] - rel[2]) / (2f * sinAngle)
        val axisZ = (rel[1] - rel[3]) / (2f * sinAngle)
        val degrees = Math.toDegrees(angle.toDouble()).toFloat()
        return floatArrayOf(axisX * degrees, axisY * degrees, axisZ * degrees)
    }

    /** The movement as `(nodding, rolling)` degrees — the two axes the joystick uses. */
    fun relativeDegrees(neutral: FloatArray, current: FloatArray): FloatArray =
        relativeAxesDegrees(neutral, current).let { floatArrayOf(it[0], it[1]) }

    /**
     * The tilt as a [HeadPose]. [HeadPose.roll] drives the stick sideways and [HeadPose.pitch] drives
     * it up and down, a positive pitch reading as leaning forward so the stick goes forward.
     */
    fun pose(neutral: FloatArray, current: FloatArray): HeadPose {
        val axes = relativeAxesDegrees(neutral, current)
        return HeadPose(
            yaw = axes[2],
            pitch = -SIGN_Y * axes[0],
            roll = SIGN_X * axes[1],
        )
    }

    private fun transpose(m: FloatArray): FloatArray =
        floatArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8])

    /** Column-major 3x3 product: `out = a * b`, entry `(i, j)` at index `i + 3*j`. */
    private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(9)
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var sum = 0f
                for (k in 0 until 3) sum += a[i + 3 * k] * b[k + 3 * j]
                out[i + 3 * j] = sum
            }
        }
        return out
    }
}
