package com.pwde.app.sensors

import com.pwde.app.sensors.face.HeadPoseMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HeadPoseTest {
    /** Column-major 4x4 of R = Ry(yaw) · Rx(pitch) · Rz(roll), angles in degrees. */
    private fun matrix(yaw: Double, pitch: Double, roll: Double): FloatArray {
        val a = Math.toRadians(yaw)
        val b = Math.toRadians(pitch)
        val c = Math.toRadians(roll)
        val ry = arrayOf(doubleArrayOf(cos(a), 0.0, sin(a)), doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(-sin(a), 0.0, cos(a)))
        val rx = arrayOf(doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(0.0, cos(b), -sin(b)), doubleArrayOf(0.0, sin(b), cos(b)))
        val rz = arrayOf(doubleArrayOf(cos(c), -sin(c), 0.0), doubleArrayOf(sin(c), cos(c), 0.0), doubleArrayOf(0.0, 0.0, 1.0))
        fun mul(x: Array<DoubleArray>, y: Array<DoubleArray>) =
            Array(3) { i -> DoubleArray(3) { j -> (0..2).sumOf { k -> x[i][k] * y[k][j] } } }
        val r = mul(mul(ry, rx), rz)
        val out = FloatArray(16)
        for (row in 0..2) for (col in 0..2) out[col * 4 + row] = r[row][col].toFloat()
        out[15] = 1f
        return out
    }

    private fun assertRoundTrip(yaw: Double, pitch: Double, roll: Double) {
        val (y, p, r) = HeadPoseMath.rawEulerDegrees(matrix(yaw, pitch, roll))
        assertEquals(yaw.toFloat(), y, 0.01f)
        assertEquals(pitch.toFloat(), p, 0.01f)
        assertEquals(roll.toFloat(), r, 0.01f)
    }

    @Test
    fun identityIsNeutral() = assertRoundTrip(0.0, 0.0, 0.0)

    @Test
    fun singleAxisRotations() {
        assertRoundTrip(25.0, 0.0, 0.0)
        assertRoundTrip(0.0, -15.0, 0.0)
        assertRoundTrip(0.0, 0.0, 30.0)
    }

    @Test
    fun combinedRotation() = assertRoundTrip(-20.0, 10.0, -12.0)

    /** Face-space rotations for real head movements, seen in a mirrored selfie image. */
    @Test
    fun turningRightIsPositiveYaw() {
        assertTrue(HeadPoseMath.fromTransformationMatrix(matrix(20.0, 0.0, 0.0)).yaw > 0f)
    }

    @Test
    fun lookingUpIsPositivePitch() {
        // Forward axis (+z) swinging toward +y is a negative rotation about x.
        assertTrue(HeadPoseMath.fromTransformationMatrix(matrix(0.0, -15.0, 0.0)).pitch > 0f)
    }

    @Test
    fun tiltingRightIsPositiveRoll() {
        // Up axis (+y) swinging toward +x is a negative rotation about z.
        assertTrue(HeadPoseMath.fromTransformationMatrix(matrix(0.0, 0.0, -15.0)).roll > 0f)
    }

    @Test
    fun userFacingSignsAreApplied() {
        // Only the sign convention differs between raw and user-facing values.
        val pose = HeadPoseMath.fromTransformationMatrix(matrix(20.0, 10.0, 15.0))
        val (y, p, r) = HeadPoseMath.rawEulerDegrees(matrix(20.0, 10.0, 15.0))
        assertEquals(kotlin.math.abs(y), kotlin.math.abs(pose.yaw), 0.01f)
        assertEquals(kotlin.math.abs(p), kotlin.math.abs(pose.pitch), 0.01f)
        assertEquals(kotlin.math.abs(r), kotlin.math.abs(pose.roll), 0.01f)
    }
}
