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
    fun eyebrowRaise() = assertDetects(
        FacialGesture.EYEBROW_RAISE,
        mapOf(Blendshapes.BROW_OUTER_UP_LEFT to 0.8f, Blendshapes.BROW_OUTER_UP_RIGHT to 0.8f, Blendshapes.BROW_INNER_UP to 0.7f),
    )

    @Test
    fun openMouth() = assertDetects(FacialGesture.OPEN_MOUTH, mapOf(Blendshapes.JAW_OPEN to 0.7f))

    @Test
    fun closingBothEyesIsDetected() {
        val closed = mapOf(Blendshapes.EYE_BLINK_LEFT to 0.9f, Blendshapes.EYE_BLINK_RIGHT to 0.9f)
        // Held for three times the usual debounce, so an ordinary blink doesn't count.
        assertTrue(FacialGesture.CLOSE_EYES in GestureClassifier().feed(closed, frames = 12).active)
    }

    @Test
    fun aBlinkIsTooShortToCloseBothEyes() {
        val closed = mapOf(Blendshapes.EYE_BLINK_LEFT to 0.9f, Blendshapes.EYE_BLINK_RIGHT to 0.9f)
        assertFalse(FacialGesture.CLOSE_EYES in GestureClassifier().feed(closed, frames = 3).active)
    }

    @Test
    fun oneEyeClosedIsNotBothEyesClosed() {
        val leftOnly = mapOf(Blendshapes.EYE_BLINK_LEFT to 0.9f, Blendshapes.EYE_BLINK_RIGHT to 0f)
        assertFalse(FacialGesture.CLOSE_EYES in GestureClassifier().feed(leftOnly, frames = 12).active)
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
    fun catalogDropsTheRemovedGestures() {
        // These can't be mapped to a button, so they're gone from the catalog entirely.
        val removed = listOf(
            "FROWN", "WINK", "WINK_LEFT", "WINK_RIGHT", "CHEEK_PUFF", "JAW_LEFT", "JAW_RIGHT",
            "LOWER_LEFT_EYEBROW", "LOWER_RIGHT_EYEBROW", "LOOK_UP", "LOOK_DOWN",
            "MP_CHEEK_PUFF", "MP_JAW_LEFT", "MP_JAW_RIGHT", "MP_BROW_DOWN_LEFT", "MP_BROW_DOWN_RIGHT",
            "MP_EYE_BLINK_LEFT", "MP_EYE_BLINK_RIGHT", "MP_MOUTH_FROWN_LEFT", "MP_MOUTH_FROWN_RIGHT",
            "MP_EYE_LOOK_UP_LEFT", "MP_EYE_LOOK_UP_RIGHT", "MP_EYE_LOOK_DOWN_LEFT", "MP_EYE_LOOK_DOWN_RIGHT",
            "MP_EYE_LOOK_IN_LEFT", "MP_EYE_LOOK_IN_RIGHT", "MP_EYE_LOOK_OUT_LEFT", "MP_EYE_LOOK_OUT_RIGHT",
        )
        val names = FacialGesture.entries.map { it.name }
        assertEquals(emptyList<String>(), removed.filter { it in names })
        // Close both eyes stays: it was unreadable before and is measured again.
        assertTrue(FacialGesture.CLOSE_EYES in FacialGesture.curated)
    }

    @Test
    fun catalogHasTheRemainingMediaPipeBlendshapes() {
        val names = FacialGesture.raw.map { it.blendshape }
        assertEquals(35, names.size)
        assertEquals(35, names.toSet().size)
        assertTrue("_neutral" in names && "noseSneerRight" in names && "mouthUpperUpLeft" in names)
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
        assertEquals("brow inner up", FacialGesture.MP_BROW_INNER_UP.spokenName)
        assertEquals("neutral", FacialGesture.MP_NEUTRAL.spokenName)
        assertEquals("smile", FacialGesture.SMILE.spokenName)
    }

    @Test
    fun mouthGestures() {
        assertDetects(FacialGesture.MOUTH_LEFT, mapOf(Blendshapes.side(Blendshapes.MOUTH_LEFT, Blendshapes.MOUTH_RIGHT, true) to 0.8f))
        assertDetects(FacialGesture.MOUTH_RIGHT, mapOf(Blendshapes.side(Blendshapes.MOUTH_LEFT, Blendshapes.MOUTH_RIGHT, false) to 0.8f))
        assertDetects(FacialGesture.PUCKER, mapOf(Blendshapes.MOUTH_PUCKER to 0.8f))
        assertDetects(FacialGesture.ROLL_LOWER_LIP, mapOf(Blendshapes.MOUTH_ROLL_LOWER to 0.8f))
    }

    @Test
    fun singleEyebrowGestures() {
        val leftUp = Blendshapes.side(Blendshapes.BROW_OUTER_UP_LEFT, Blendshapes.BROW_OUTER_UP_RIGHT, true)
        val rightUp = Blendshapes.side(Blendshapes.BROW_OUTER_UP_LEFT, Blendshapes.BROW_OUTER_UP_RIGHT, false)
        val raised = GestureClassifier().feed(mapOf(leftUp to 0.8f))
        assertTrue(FacialGesture.RAISE_LEFT_EYEBROW in raised.active)
        assertFalse(FacialGesture.RAISE_RIGHT_EYEBROW in raised.active)
        assertDetects(FacialGesture.RAISE_RIGHT_EYEBROW, mapOf(rightUp to 0.8f))
    }

    @Test
    fun eyeSquintAndWideBlendshapesStillFireOnTheirOwn() {
        assertDetects(FacialGesture.MP_EYE_SQUINT_LEFT, mapOf("eyeSquintLeft" to 0.8f))
        assertDetects(FacialGesture.MP_EYE_WIDE_RIGHT, mapOf("eyeWideRight" to 0.8f))
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
