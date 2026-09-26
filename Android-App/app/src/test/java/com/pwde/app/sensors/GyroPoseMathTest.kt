package com.pwde.app.sensors

import com.pwde.app.sensors.face.GyroPoseMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The gyro's rotation maths, ported from the reference implementation's own tests. Two properties
 * carry the whole feel:
 *
 * - each of the three axes must move **only** its own stick axis, because an Euler decomposition
 *   couples them and that is felt as the stick sliding sideways when the user only tips;
 * - rotation about the screen axis — twisting the phone in its own plane — must be **ignored
 *   outright**, because it is the least usable movement and letting it steer is what made the stick
 *   wander while the phone was held still.
 *
 * Layout is 3x3 column-major, entry `(row, col)` at index `row + 3*col`, as
 * `SensorManager.getRotationMatrixFromVector` produces.
 */
class GyroPoseMathTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** Rotation by [degrees] about the X axis (nodding). */
    private fun rotX(degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return floatArrayOf(1f, 0f, 0f, 0f, c, s, 0f, -s, c)
    }

    /** Rotation by [degrees] about the Y axis (the long axis: steering). */
    private fun rotY(degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return floatArrayOf(c, 0f, -s, 0f, 1f, 0f, s, 0f, c)
    }

    /** Rotation by [degrees] about the Z axis (the screen axis: twisting). */
    private fun rotZ(degrees: Float): FloatArray {
        val r = Math.toRadians(degrees.toDouble())
        val c = cos(r).toFloat()
        val s = sin(r).toFloat()
        return floatArrayOf(c, s, 0f, -s, c, 0f, 0f, 0f, 1f)
    }

    private fun relative(neutral: FloatArray, current: FloatArray) =
        GyroPoseMath.relativeDegrees(neutral, current)

    @Test
    fun holdingStillIsNoMovement() {
        val rel = relative(identity, identity)
        assertEquals(0f, rel[0], TOLERANCE)
        assertEquals(0f, rel[1], TOLERANCE)
        // Compared with a tolerance on purpose: the sign constants turn a zero into -0.0f, which
        // Kotlin's data-class equality counts as different from 0.0f.
        val still = GyroPoseMath.pose(identity, identity)
        assertEquals(0f, still.yaw, TOLERANCE)
        assertEquals(0f, still.pitch, TOLERANCE)
        assertEquals(0f, still.roll, TOLERANCE)
    }

    @Test
    fun aNodMovesOnlyTheUpDownAxis() {
        val forward = relative(identity, rotX(-30f))
        assertEquals(-30f, forward[0], TOLERANCE)
        assertEquals(0f, forward[1], TOLERANCE)
        val back = relative(identity, rotX(25f))
        assertEquals(25f, back[0], TOLERANCE)
        assertEquals(0f, back[1], TOLERANCE)
    }

    @Test
    fun aRollMovesOnlyTheSidewaysAxis() {
        val rel = relative(identity, rotY(20f))
        assertEquals(0f, rel[0], TOLERANCE)
        assertEquals(20f, rel[1], TOLERANCE)
    }

    /** The one that made the stick wander: twisting the phone within the screen plane must not steer. */
    @Test
    fun twistingAboutTheScreenAxisIsIgnored() {
        val rel = relative(identity, rotZ(45f))
        assertEquals(0f, rel[0], TOLERANCE)
        assertEquals(0f, rel[1], TOLERANCE)
        val pose = GyroPoseMath.pose(identity, rotZ(45f))
        assertEquals(0f, pose.pitch, TOLERANCE)
        assertEquals(0f, pose.roll, TOLERANCE)
    }

    /** The neutral is a pose, not zero: only the movement away from it counts. */
    @Test
    fun theNeutralPoseIsSubtracted() {
        val rel = relative(rotX(20f), rotX(50f))
        assertEquals(30f, rel[0], TOLERANCE)
        assertEquals(0f, rel[1], TOLERANCE)
        assertEquals(0f, relative(rotY(-40f), rotY(-40f))[1], TOLERANCE)
    }

    /**
     * The pitch axis carries the head's meaning, with the sign the *device* settled. The first port
     * had it the other way round and drove the stick backwards when the phone was tipped forward —
     * "invert the up and down of the gyro", 2026-09-26. This pins the corrected direction so a later
     * refactor cannot quietly put it back.
     */
    @Test
    fun thePitchAxisCarriesTheHeadPoseMeaningWithTheDevicesSign() {
        assertTrue(GyroPoseMath.pose(identity, rotX(30f)).pitch > 0f)
        assertTrue(GyroPoseMath.pose(identity, rotX(-30f)).pitch < 0f)
    }

    /** Steering is one signed axis, as the reference's SIGN_X = -1 gives it. */
    @Test
    fun theRollAxisSteersOneWayPerSign() {
        assertEquals(-20f, GyroPoseMath.pose(identity, rotY(20f)).roll, TOLERANCE)
        assertEquals(20f, GyroPoseMath.pose(identity, rotY(-20f)).roll, TOLERANCE)
    }

    /** Both sources must be capable of reaching the rim: the tilt is the only difference. */
    @Test
    fun theFullTiltMatchesTheReference() {
        assertEquals(40f, GyroPoseMath.FULL_TILT_DEGREES, 0.001f)
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}
