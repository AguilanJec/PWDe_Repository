package com.pwde.app.sensors

import com.pwde.app.data.model.FacialGesture
import com.pwde.app.sensors.face.Blendshapes
import com.pwde.app.sensors.face.GestureClassifier
import com.pwde.app.sensors.face.GestureReading
import com.pwde.app.sensors.face.GestureThresholds
import com.pwde.app.sensors.face.HeadPose
import com.pwde.app.sensors.face.NodDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureClassifierTest {
    private var t = 0L

    /** Feeds the same frame [frames] times, 33 ms apart; returns the last reading. */
    private fun GestureClassifier.feed(
        blendshapes: Map<String, Float>,
        pose: HeadPose? = HeadPose.NEUTRAL,
        frames: Int = 3,
        sensitivity: (FacialGesture) -> Int = { 5 },
    ): GestureReading {
        var reading = GestureReading.NONE
        repeat(frames) {
            t += 33
            reading = classify(blendshapes, pose, t, sensitivity)
        }
        return reading
    }

    private fun assertDetects(gesture: FacialGesture, blendshapes: Map<String, Float>, pose: HeadPose = HeadPose.NEUTRAL) {
        val reading = GestureClassifier().feed(blendshapes, pose)
        assertTrue("$gesture not detected: ${reading.active}", gesture in reading.active)
    }

    @Test
    fun smile() = assertDetects(FacialGesture.SMILE, mapOf(Blendshapes.MOUTH_SMILE_LEFT to 0.9f, Blendshapes.MOUTH_SMILE_RIGHT to 0.8f))

    @Test
    fun frownUsesMouthAndBrows() = assertDetects(
        FacialGesture.FROWN,
        mapOf(
            Blendshapes.MOUTH_FROWN_LEFT to 0.6f, Blendshapes.MOUTH_FROWN_RIGHT to 0.6f,
            Blendshapes.BROW_DOWN_LEFT to 0.7f, Blendshapes.BROW_DOWN_RIGHT to 0.7f,
        ),
    )

    @Test
    fun eyebrowRaise() = assertDetects(
        FacialGesture.EYEBROW_RAISE,
        mapOf(Blendshapes.BROW_OUTER_UP_LEFT to 0.8f, Blendshapes.BROW_OUTER_UP_RIGHT to 0.8f, Blendshapes.BROW_INNER_UP to 0.7f),
    )

    @Test
    fun openMouth() = assertDetects(FacialGesture.OPEN_MOUTH, mapOf(Blendshapes.JAW_OPEN to 0.7f))

    @Test
    fun wink() = assertDetects(FacialGesture.WINK, mapOf(Blendshapes.EYE_BLINK_LEFT to 0.9f, Blendshapes.EYE_BLINK_RIGHT to 0.05f))

    @Test
    fun blinkIsNotAWink() {
        val reading = GestureClassifier().feed(mapOf(Blendshapes.EYE_BLINK_LEFT to 0.9f, Blendshapes.EYE_BLINK_RIGHT to 0.9f))
        assertFalse(FacialGesture.WINK in reading.active)
    }

    @Test
    fun tiltLeftAndRight() {
        assertDetects(FacialGesture.TILT_RIGHT, emptyMap(), HeadPose(0f, 0f, 25f))
        assertDetects(FacialGesture.TILT_LEFT, emptyMap(), HeadPose(0f, 0f, -25f))
    }

    @Test
    fun tiltIsMeasuredFromTheUsersNeutral() {
        val classifier = GestureClassifier()
        var reading = GestureReading.NONE
        repeat(3) {
            t += 33
            reading = classifier.classify(emptyMap(), HeadPose(0f, 0f, 25f), t, neutral = HeadPose(0f, 0f, 20f))
        }
        assertFalse(FacialGesture.TILT_RIGHT in reading.active)
    }

    @Test
    fun nodDownAndBackUp() {
        val classifier = GestureClassifier()
        val pitches = listOf(0f, -5f, -12f, -18f, -12f, -4f, 0f)
        val started = mutableSetOf<FacialGesture>()
        pitches.forEach { pitch ->
            t += 80
            started += classifier.classify(emptyMap(), HeadPose(0f, pitch, 0f), t).started
        }
        assertTrue(FacialGesture.NOD in started)
    }

    @Test
    fun debounceNeedsThreeFrames() {
        val classifier = GestureClassifier()
        val smile = mapOf(Blendshapes.MOUTH_SMILE_LEFT to 0.9f, Blendshapes.MOUTH_SMILE_RIGHT to 0.9f)
        assertFalse(FacialGesture.SMILE in classifier.feed(smile, frames = 2).active)
        val third = classifier.feed(smile, frames = 1)
        assertTrue(FacialGesture.SMILE in third.active)
        assertEquals(setOf(FacialGesture.SMILE), third.started.filterNot { it.isRaw }.toSet())
        assertTrue(FacialGesture.MP_MOUTH_SMILE_LEFT in third.started)
        // Still held: active, but not "started" again.
        val fourth = classifier.feed(smile, frames = 1)
        assertTrue(FacialGesture.SMILE in fourth.active)
        assertTrue(fourth.started.isEmpty())
    }

    @Test
    fun hysteresisKeepsGestureUntilClearlyReleased() {
        val classifier = GestureClassifier()
        val threshold = GestureThresholds.blendshape(5)
        classifier.feed(mapOf(Blendshapes.JAW_OPEN to threshold + 0.1f))
        val slightlyBelow = classifier.feed(mapOf(Blendshapes.JAW_OPEN to threshold * 0.9f), frames = 1)
        assertTrue(FacialGesture.OPEN_MOUTH in slightlyBelow.active)
        val released = classifier.feed(mapOf(Blendshapes.JAW_OPEN to threshold * 0.5f), frames = 1)
        assertFalse(FacialGesture.OPEN_MOUTH in released.active)
    }

    @Test
    fun higherSensitivityLowersThresholds() {
        assertTrue(GestureThresholds.blendshape(10) < GestureThresholds.blendshape(1))
        assertTrue(GestureThresholds.tiltDegrees(10) < GestureThresholds.tiltDegrees(1))
        assertTrue(GestureThresholds.nodDegrees(10) < GestureThresholds.nodDegrees(1))
        assertEquals(GestureThresholds.BLENDSHAPE_AT_MIN, GestureThresholds.blendshape(1), 1e-6f)
        assertEquals(GestureThresholds.BLENDSHAPE_AT_MAX, GestureThresholds.blendshape(10), 1e-6f)
    }

    @Test
    fun sensitivityDecidesWhetherAWeakSmileCounts() {
        val weakSmile = mapOf(Blendshapes.MOUTH_SMILE_LEFT to 0.35f, Blendshapes.MOUTH_SMILE_RIGHT to 0.35f)
        assertFalse(FacialGesture.SMILE in GestureClassifier().feed(weakSmile, sensitivity = { 3 }).active)
        assertTrue(FacialGesture.SMILE in GestureClassifier().feed(weakSmile, sensitivity = { 10 }).active)
    }

    @Test
    fun poseOnlyInputCanOnlyDoHeadMoves() {
        val reading = GestureClassifier().feed(emptyMap(), HeadPose(0f, 0f, 0f))
        assertEquals(
            setOf(FacialGesture.TILT_LEFT, FacialGesture.TILT_RIGHT, FacialGesture.NOD, FacialGesture.SHAKE),
            reading.measures.keys,
        )
    }

    @Test
    fun everyGestureIsMeasuredWithAFaceInView() {
        val reading = GestureClassifier().feed(mapOf(Blendshapes.JAW_OPEN to 0f), HeadPose.NEUTRAL)
        assertEquals(FacialGesture.entries.toSet(), reading.measures.keys)
    }

    @Test
    fun catalogHasAll52MediaPipeBlendshapes() {
        val names = FacialGesture.raw.map { it.blendshape }
        assertEquals(52, names.size)
        assertEquals(52, names.toSet().size)
        assertTrue("_neutral" in names && "noseSneerRight" in names && "eyeLookInLeft" in names)
        assertEquals(FacialGesture.entries.size, FacialGesture.curated.size + FacialGesture.raw.size)
    }

    @Test
    fun eachRawBlendshapeFiresFromItsOwnScore() {
        val gesture = FacialGesture.MP_NOSE_SNEER_LEFT
        val reading = GestureClassifier().feed(mapOf("noseSneerLeft" to 0.9f))
        assertTrue(gesture in reading.active)
        assertEquals(0.9f, reading.measures.getValue(gesture).score, 0f)
        assertFalse(FacialGesture.MP_NOSE_SNEER_RIGHT in reading.active)
    }

    @Test
    fun rawBlendshapesHaveSpeakableNames() {
        assertEquals("brow down left", FacialGesture.MP_BROW_DOWN_LEFT.spokenName)
        assertEquals("neutral", FacialGesture.MP_NEUTRAL.spokenName)
        assertEquals("smile", FacialGesture.SMILE.spokenName)
    }

    @Test
    fun mouthAndJawGestures() {
        assertDetects(FacialGesture.MOUTH_LEFT, mapOf(Blendshapes.side(Blendshapes.MOUTH_LEFT, Blendshapes.MOUTH_RIGHT, true) to 0.8f))
        assertDetects(FacialGesture.MOUTH_RIGHT, mapOf(Blendshapes.side(Blendshapes.MOUTH_LEFT, Blendshapes.MOUTH_RIGHT, false) to 0.8f))
        assertDetects(FacialGesture.PUCKER, mapOf(Blendshapes.MOUTH_PUCKER to 0.8f))
        assertDetects(FacialGesture.CHEEK_PUFF, mapOf(Blendshapes.CHEEK_PUFF to 0.8f))
        assertDetects(FacialGesture.ROLL_LOWER_LIP, mapOf(Blendshapes.MOUTH_ROLL_LOWER to 0.8f))
        assertDetects(FacialGesture.JAW_LEFT, mapOf(Blendshapes.side(Blendshapes.JAW_LEFT, Blendshapes.JAW_RIGHT, true) to 0.8f))
        assertDetects(FacialGesture.JAW_RIGHT, mapOf(Blendshapes.side(Blendshapes.JAW_LEFT, Blendshapes.JAW_RIGHT, false) to 0.8f))
    }

    @Test
    fun singleEyebrowGestures() {
        val leftUp = Blendshapes.side(Blendshapes.BROW_OUTER_UP_LEFT, Blendshapes.BROW_OUTER_UP_RIGHT, true)
        val rightDown = Blendshapes.side(Blendshapes.BROW_DOWN_LEFT, Blendshapes.BROW_DOWN_RIGHT, false)
        val raised = GestureClassifier().feed(mapOf(leftUp to 0.8f))
        assertTrue(FacialGesture.RAISE_LEFT_EYEBROW in raised.active)
        assertFalse(FacialGesture.RAISE_RIGHT_EYEBROW in raised.active)
        assertDetects(FacialGesture.LOWER_RIGHT_EYEBROW, mapOf(rightDown to 0.8f))
    }

    @Test
    fun oneSidedWinks() {
        val leftEye = Blendshapes.side(Blendshapes.EYE_BLINK_LEFT, Blendshapes.EYE_BLINK_RIGHT, true)
        val rightEye = Blendshapes.side(Blendshapes.EYE_BLINK_LEFT, Blendshapes.EYE_BLINK_RIGHT, false)
        val left = GestureClassifier().feed(mapOf(leftEye to 0.9f, rightEye to 0.05f))
        assertTrue(FacialGesture.WINK_LEFT in left.active)
        assertFalse(FacialGesture.WINK_RIGHT in left.active)
        assertTrue(FacialGesture.WINK in left.active)
        assertDetects(FacialGesture.WINK_RIGHT, mapOf(leftEye to 0.05f, rightEye to 0.9f))
    }

    @Test
    fun closingBothEyesMustOutlastABlink() {
        val closed = mapOf(Blendshapes.EYE_BLINK_LEFT to 0.9f, Blendshapes.EYE_BLINK_RIGHT to 0.9f)
        val classifier = GestureClassifier()
        assertFalse(FacialGesture.CLOSE_EYES in classifier.feed(closed, frames = 4).active)
        assertTrue(FacialGesture.CLOSE_EYES in classifier.feed(closed, frames = 5).active)
    }

    @Test
    fun eyeGaze() {
        assertDetects(FacialGesture.LOOK_UP, mapOf(Blendshapes.EYE_LOOK_UP_LEFT to 0.8f, Blendshapes.EYE_LOOK_UP_RIGHT to 0.8f))
        assertDetects(FacialGesture.LOOK_DOWN, mapOf(Blendshapes.EYE_LOOK_DOWN_LEFT to 0.8f, Blendshapes.EYE_LOOK_DOWN_RIGHT to 0.8f))
    }

    @Test
    fun shakeLeftRightAndBack() {
        val classifier = GestureClassifier()
        val started = mutableSetOf<FacialGesture>()
        listOf(0f, 8f, 16f, 6f, -4f, 0f).forEach { yaw ->
            t += 80
            started += classifier.classify(emptyMap(), HeadPose(yaw, 0f, 0f), t).started
        }
        assertTrue(FacialGesture.SHAKE in started)
        assertFalse(FacialGesture.NOD in started)
    }
}

class NodDetectorTest {
    @Test
    fun swingThatReturnsInsideTheWindowFires() {
        val nod = NodDetector(windowMs = 800)
        var fired = false
        listOf(0f, -6f, -14f, -8f, -1f).forEachIndexed { i, p -> fired = fired || nod.update(p, i * 100L, 10f).fired }
        assertTrue(fired)
    }

    @Test
    fun slowSwingOutsideTheWindowDoesNotFire() {
        val nod = NodDetector(windowMs = 800)
        var fired = false
        listOf(0f, -6f, -14f, -8f, -1f).forEachIndexed { i, p -> fired = fired || nod.update(p, i * 400L, 10f).fired }
        assertFalse(fired)
    }

    @Test
    fun lookingDownAndStayingThereIsNotANod() {
        val nod = NodDetector()
        var fired = false
        listOf(0f, -6f, -14f, -15f, -15f).forEachIndexed { i, p -> fired = fired || nod.update(p, i * 100L, 10f).fired }
        assertFalse(fired)
    }

    @Test
    fun smallSwingBelowThresholdDoesNotFire() {
        val nod = NodDetector()
        var fired = false
        listOf(0f, -3f, -6f, -3f, 0f).forEachIndexed { i, p -> fired = fired || nod.update(p, i * 100L, 10f).fired }
        assertFalse(fired)
    }
}
