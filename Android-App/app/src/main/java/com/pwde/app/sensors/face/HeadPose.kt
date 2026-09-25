package com.pwde.app.sensors.face

import kotlin.math.asin
import kotlin.math.atan2

/**
 * Head orientation in degrees, from the user's point of view:
 * [yaw] > 0 turned to their right, [pitch] > 0 looking up, [roll] > 0 tilted toward their right shoulder.
 */
data class HeadPose(val yaw: Float, val pitch: Float, val roll: Float) {
    companion object {
        val NEUTRAL = HeadPose(0f, 0f, 0f)
    }
}

object HeadPoseMath {
    private const val RAD_TO_DEG = (180.0 / Math.PI).toFloat()

    /**
     * Sign corrections from MediaPipe's face space (+x right, +y up, +z toward the camera) to the
     * user's point of view, for a mirrored (selfie) input image:
     * - turning right swings the face's forward axis toward +x → raw yaw is already positive;
     * - looking up swings it toward +y, a negative rotation about x → raw pitch must be negated;
     * - tilting toward the right shoulder swings the face's up axis toward +x, a negative rotation
     *   about z → raw roll must be negated.
     * If a device reports a direction backwards, flip the sign here and nowhere else.
     */
    private const val YAW_SIGN = 1f
    private const val PITCH_SIGN = -1f
    private const val ROLL_SIGN = -1f

    /**
     * Decomposes MediaPipe's facial transformation matrix (4×4, column-major) into Euler angles,
     * treating the rotation as R = Ry(yaw) · Rx(pitch) · Rz(roll).
     */
    fun fromTransformationMatrix(matrix: FloatArray): HeadPose {
        require(matrix.size >= 16) { "Expected a 4x4 matrix" }
        fun r(row: Int, col: Int) = matrix[col * 4 + row]
        val pitch = asin((-r(1, 2)).coerceIn(-1f, 1f))
        val yaw = atan2(r(0, 2), r(2, 2))
        val roll = atan2(r(1, 0), r(1, 1))
        return HeadPose(
            yaw = YAW_SIGN * yaw * RAD_TO_DEG,
            pitch = PITCH_SIGN * pitch * RAD_TO_DEG,
            roll = ROLL_SIGN * roll * RAD_TO_DEG,
        )
    }

    /** Raw (unsigned-corrected) Euler angles in degrees, exposed for tests. */
    internal fun rawEulerDegrees(matrix: FloatArray): Triple<Float, Float, Float> {
        val pose = fromTransformationMatrix(matrix)
        return Triple(pose.yaw / YAW_SIGN, pose.pitch / PITCH_SIGN, pose.roll / ROLL_SIGN)
    }
}
