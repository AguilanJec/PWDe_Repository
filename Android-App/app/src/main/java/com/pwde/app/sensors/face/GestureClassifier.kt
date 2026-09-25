package com.pwde.app.sensors.face

import com.pwde.app.data.model.DEFAULT_LEVEL
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.MAX_LEVEL
import com.pwde.app.data.model.MIN_LEVEL
import kotlin.math.abs

/*
 * Blendshape-threshold gesture triggers, adapted from Google Project GameFace
 * (https://github.com/google/project-gameface, Apache License 2.0): each gesture is a score
 * compared against a user-tunable threshold. Head tilt and nod come from head pose, not blendshapes.
 */

/** One gesture's live measurement: [score] and [threshold] are in the same unit (0–1 or degrees). */
data class GestureMeasure(val score: Float, val threshold: Float) {
    /** How close the score is to firing: 1.0 means exactly at the threshold. */
    val ratio: Float get() = if (threshold <= 0f) 0f else score / threshold
}

data class GestureReading(
    val measures: Map<FacialGesture, GestureMeasure> = emptyMap(),
    /** Gestures currently held past their threshold (debounced). */
    val active: Set<FacialGesture> = emptySet(),
    /** Gestures that became active on this frame. */
    val started: Set<FacialGesture> = emptySet(),
) {
    /** The strongest active gesture, if any. */
    val primary: FacialGesture?
        get() = active.maxByOrNull { measures[it]?.ratio ?: 0f }

    companion object {
        val NONE = GestureReading()
    }
}

/** Sensitivity level (1–10) → threshold. Higher sensitivity fires on a smaller movement. */
object GestureThresholds {
    const val BLENDSHAPE_AT_MIN = 0.80f
    const val BLENDSHAPE_AT_MAX = 0.20f
    const val TILT_DEGREES_AT_MIN = 30f
    const val TILT_DEGREES_AT_MAX = 8f
    const val NOD_DEGREES_AT_MIN = 20f
    const val NOD_DEGREES_AT_MAX = 5f

    fun blendshape(level: Int) = lerp(BLENDSHAPE_AT_MIN, BLENDSHAPE_AT_MAX, level)
    fun tiltDegrees(level: Int) = lerp(TILT_DEGREES_AT_MIN, TILT_DEGREES_AT_MAX, level)
    fun nodDegrees(level: Int) = lerp(NOD_DEGREES_AT_MIN, NOD_DEGREES_AT_MAX, level)

    fun forGesture(gesture: FacialGesture, level: Int): Float = when (gesture) {
        FacialGesture.TILT_LEFT, FacialGesture.TILT_RIGHT -> tiltDegrees(level)
        FacialGesture.NOD, FacialGesture.SHAKE -> nodDegrees(level)
        else -> blendshape(level)
    }

    private fun lerp(atMin: Float, atMax: Float, level: Int): Float {
        val t = (level.coerceIn(MIN_LEVEL, MAX_LEVEL) - MIN_LEVEL).toFloat() / (MAX_LEVEL - MIN_LEVEL)
        return atMin + (atMax - atMin) * t
    }
}

/** MediaPipe blendshape category names used by the classifier. */
object Blendshapes {
    const val MOUTH_SMILE_LEFT = "mouthSmileLeft"
    const val MOUTH_SMILE_RIGHT = "mouthSmileRight"
    const val MOUTH_FROWN_LEFT = "mouthFrownLeft"
    const val MOUTH_FROWN_RIGHT = "mouthFrownRight"
    const val BROW_DOWN_LEFT = "browDownLeft"
    const val BROW_DOWN_RIGHT = "browDownRight"
    const val BROW_OUTER_UP_LEFT = "browOuterUpLeft"
    const val BROW_OUTER_UP_RIGHT = "browOuterUpRight"
    const val BROW_INNER_UP = "browInnerUp"
    const val JAW_OPEN = "jawOpen"
    const val EYE_BLINK_LEFT = "eyeBlinkLeft"
    const val EYE_BLINK_RIGHT = "eyeBlinkRight"
    const val MOUTH_LEFT = "mouthLeft"
    const val MOUTH_RIGHT = "mouthRight"
    const val MOUTH_PUCKER = "mouthPucker"
    const val CHEEK_PUFF = "cheekPuff"
    const val MOUTH_ROLL_LOWER = "mouthRollLower"
    const val JAW_LEFT = "jawLeft"
    const val JAW_RIGHT = "jawRight"
    const val EYE_LOOK_UP_LEFT = "eyeLookUpLeft"
    const val EYE_LOOK_UP_RIGHT = "eyeLookUpRight"
    const val EYE_LOOK_DOWN_LEFT = "eyeLookDownLeft"
    const val EYE_LOOK_DOWN_RIGHT = "eyeLookDownRight"

    /**
     * Whether MediaPipe's "Left"/"Right" categories name the opposite side of the user's face on
     * our mirrored (selfie) input. Check "Wink left" in Testing Station — if closing your left eye
     * shows "Wink right", flip this and nothing else.
     */
    const val SIDES_SWAPPED = false

    /** The category for the user's [left] or right side of a Left/Right pair. */
    fun side(leftName: String, rightName: String, left: Boolean): String =
        if (left != SIDES_SWAPPED) leftName else rightName
}

/**
 * Turns blendshape scores + head pose into debounced gesture readings. Stateful (debounce, nod
 * history), so use one instance per tracking session and feed it frames in timestamp order.
 */
class GestureClassifier(
    private val holdFrames: Int = 3,
    private val nodDetector: NodDetector = NodDetector(),
    private val shakeDetector: NodDetector = NodDetector(),
) {
    private val heldFor = mutableMapOf<FacialGesture, Int>()
    private var active = emptySet<FacialGesture>()

    /**
     * @param blendshapes MediaPipe category name → score. Empty when only head pose is available
     *   (simulated tracking), in which case only head moves (tilt, nod, shake) can fire.
     * @param neutral the user's resting pose; tilt and nod are measured relative to it.
     */
    fun classify(
        blendshapes: Map<String, Float>,
        pose: HeadPose?,
        timestampMs: Long,
        sensitivity: (FacialGesture) -> Int = { DEFAULT_LEVEL },
        neutral: HeadPose = HeadPose.NEUTRAL,
    ): GestureReading {
        val measures = mutableMapOf<FacialGesture, GestureMeasure>()
        fun threshold(g: FacialGesture) = GestureThresholds.forGesture(g, sensitivity(g))
        fun b(name: String) = blendshapes[name] ?: 0f

        if (blendshapes.isNotEmpty()) {
            measures[FacialGesture.SMILE] = GestureMeasure(
                avg(b(Blendshapes.MOUTH_SMILE_LEFT), b(Blendshapes.MOUTH_SMILE_RIGHT)), threshold(FacialGesture.SMILE),
            )
            // Frown alone is weak in MediaPipe's model, so lowered brows reinforce it.
            val frown = FROWN_MOUTH_WEIGHT * avg(b(Blendshapes.MOUTH_FROWN_LEFT), b(Blendshapes.MOUTH_FROWN_RIGHT)) +
                FROWN_BROW_WEIGHT * avg(b(Blendshapes.BROW_DOWN_LEFT), b(Blendshapes.BROW_DOWN_RIGHT))
            measures[FacialGesture.FROWN] = GestureMeasure(frown, threshold(FacialGesture.FROWN))
            measures[FacialGesture.EYEBROW_RAISE] = GestureMeasure(
                avg(b(Blendshapes.BROW_OUTER_UP_LEFT), b(Blendshapes.BROW_OUTER_UP_RIGHT), b(Blendshapes.BROW_INNER_UP)),
                threshold(FacialGesture.EYEBROW_RAISE),
            )
            measures[FacialGesture.OPEN_MOUTH] = GestureMeasure(b(Blendshapes.JAW_OPEN), threshold(FacialGesture.OPEN_MOUTH))

            // Wink: one eye closed while the other stays open. A plain blink closes both.
            val winkThreshold = threshold(FacialGesture.WINK)
            val left = b(Blendshapes.EYE_BLINK_LEFT)
            val right = b(Blendshapes.EYE_BLINK_RIGHT)
            val closed = maxOf(left, right)
            val open = minOf(left, right)
            val winkScore = if (open < winkThreshold * WINK_OPEN_EYE_FRACTION) closed else 0f
            measures[FacialGesture.WINK] = GestureMeasure(winkScore, winkThreshold)

            fun userSide(left: String, right: String, userLeft: Boolean) = b(Blendshapes.side(left, right, userLeft))
            fun put(g: FacialGesture, score: Float) {
                measures[g] = GestureMeasure(score, threshold(g))
            }

            // Mouth and jaw (GameFace's mouth-left/right and roll-lower-lip, plus pucker, cheek puff, jaw slides).
            put(FacialGesture.MOUTH_LEFT, userSide(Blendshapes.MOUTH_LEFT, Blendshapes.MOUTH_RIGHT, true))
            put(FacialGesture.MOUTH_RIGHT, userSide(Blendshapes.MOUTH_LEFT, Blendshapes.MOUTH_RIGHT, false))
            put(FacialGesture.PUCKER, b(Blendshapes.MOUTH_PUCKER))
            put(FacialGesture.CHEEK_PUFF, b(Blendshapes.CHEEK_PUFF))
            put(FacialGesture.ROLL_LOWER_LIP, b(Blendshapes.MOUTH_ROLL_LOWER))
            put(FacialGesture.JAW_LEFT, userSide(Blendshapes.JAW_LEFT, Blendshapes.JAW_RIGHT, true))
            put(FacialGesture.JAW_RIGHT, userSide(Blendshapes.JAW_LEFT, Blendshapes.JAW_RIGHT, false))

            // One eyebrow at a time.
            put(FacialGesture.RAISE_LEFT_EYEBROW, userSide(Blendshapes.BROW_OUTER_UP_LEFT, Blendshapes.BROW_OUTER_UP_RIGHT, true))
            put(FacialGesture.RAISE_RIGHT_EYEBROW, userSide(Blendshapes.BROW_OUTER_UP_LEFT, Blendshapes.BROW_OUTER_UP_RIGHT, false))
            put(FacialGesture.LOWER_LEFT_EYEBROW, userSide(Blendshapes.BROW_DOWN_LEFT, Blendshapes.BROW_DOWN_RIGHT, true))
            put(FacialGesture.LOWER_RIGHT_EYEBROW, userSide(Blendshapes.BROW_DOWN_LEFT, Blendshapes.BROW_DOWN_RIGHT, false))

            // Eyes: a one-sided wink needs the other eye open; closing both is held longer than a blink.
            val userLeftEye = userSide(Blendshapes.EYE_BLINK_LEFT, Blendshapes.EYE_BLINK_RIGHT, true)
            val userRightEye = userSide(Blendshapes.EYE_BLINK_LEFT, Blendshapes.EYE_BLINK_RIGHT, false)
            val leftWinkThreshold = threshold(FacialGesture.WINK_LEFT)
            val rightWinkThreshold = threshold(FacialGesture.WINK_RIGHT)
            put(FacialGesture.WINK_LEFT, if (userRightEye < leftWinkThreshold * WINK_OPEN_EYE_FRACTION) userLeftEye else 0f)
            put(FacialGesture.WINK_RIGHT, if (userLeftEye < rightWinkThreshold * WINK_OPEN_EYE_FRACTION) userRightEye else 0f)
            put(FacialGesture.CLOSE_EYES, minOf(userLeftEye, userRightEye))
            put(FacialGesture.LOOK_UP, avg(b(Blendshapes.EYE_LOOK_UP_LEFT), b(Blendshapes.EYE_LOOK_UP_RIGHT)))
            put(FacialGesture.LOOK_DOWN, avg(b(Blendshapes.EYE_LOOK_DOWN_LEFT), b(Blendshapes.EYE_LOOK_DOWN_RIGHT)))

            // All 52 MediaPipe blendshapes, each usable on its own, scored exactly as the model reports.
            for (gesture in FacialGesture.raw) put(gesture, b(gesture.blendshape!!))
        }

        var nodFired = false
        var shakeFired = false
        if (pose != null) {
            val roll = pose.roll - neutral.roll
            measures[FacialGesture.TILT_LEFT] = GestureMeasure(maxOf(0f, -roll), threshold(FacialGesture.TILT_LEFT))
            measures[FacialGesture.TILT_RIGHT] = GestureMeasure(maxOf(0f, roll), threshold(FacialGesture.TILT_RIGHT))
            val nodThreshold = threshold(FacialGesture.NOD)
            val nod = nodDetector.update(pose.pitch - neutral.pitch, timestampMs, nodThreshold)
            nodFired = nod.fired
            measures[FacialGesture.NOD] = GestureMeasure(nod.swing, nodThreshold)
            val shakeThreshold = threshold(FacialGesture.SHAKE)
            val shake = shakeDetector.update(pose.yaw, timestampMs, shakeThreshold)
            shakeFired = shake.fired
            measures[FacialGesture.SHAKE] = GestureMeasure(shake.swing, shakeThreshold)
        } else {
            nodDetector.reset()
            shakeDetector.reset()
        }

        val nowActive = mutableSetOf<FacialGesture>()
        for ((gesture, measure) in measures) {
            if (gesture in SWING_GESTURES) continue
            val wasActive = gesture in active
            // Hysteresis: once active, a gesture stays active until it drops clearly below threshold.
            val over = measure.score >= if (wasActive) measure.threshold * RELEASE_FRACTION else measure.threshold
            val frames = if (over) (heldFor[gesture] ?: 0) + 1 else 0
            heldFor[gesture] = frames
            if (over && (wasActive || frames >= holdFramesFor(gesture))) nowActive += gesture
        }
        if (nodFired || nodDetector.isHolding(timestampMs)) nowActive += FacialGesture.NOD
        if (shakeFired || shakeDetector.isHolding(timestampMs)) nowActive += FacialGesture.SHAKE

        val started = nowActive - active
        active = nowActive
        return GestureReading(measures, nowActive, started)
    }

    fun reset() {
        heldFor.clear()
        active = emptySet()
        nodDetector.reset()
        shakeDetector.reset()
    }

    /** Closing both eyes must outlast an ordinary blink (~150 ms) before it counts. */
    private fun holdFramesFor(gesture: FacialGesture) =
        if (gesture == FacialGesture.CLOSE_EYES) holdFrames * CLOSE_EYES_HOLD_MULTIPLIER else holdFrames

    private fun avg(vararg values: Float) = values.sum() / values.size

    companion object {
        const val FROWN_MOUTH_WEIGHT = 0.6f
        const val FROWN_BROW_WEIGHT = 0.4f
        const val WINK_OPEN_EYE_FRACTION = 0.5f
        const val RELEASE_FRACTION = 0.8f
        const val CLOSE_EYES_HOLD_MULTIPLIER = 3

        /** Swing gestures fire once per movement instead of being held. */
        private val SWING_GESTURES = setOf(FacialGesture.NOD, FacialGesture.SHAKE)
    }
}

/**
 * Detects a head swing: an angle (pitch for a nod, yaw for a shake) moves away from where it
 * started by at least the threshold and comes back, all within [windowMs].
 */
class NodDetector(
    val windowMs: Long = DEFAULT_WINDOW_MS,
    private val holdMs: Long = 400,
) {
    data class Update(val fired: Boolean, val swing: Float)

    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var holdUntil = Long.MIN_VALUE

    fun update(pitch: Float, timestampMs: Long, thresholdDegrees: Float): Update {
        samples.addLast(timestampMs to pitch)
        while (samples.isNotEmpty() && timestampMs - samples.first().first > windowMs) samples.removeFirst()

        val baseline = samples.first().second
        var extremeIndex = 0
        var swing = 0f
        samples.forEachIndexed { i, (_, p) ->
            val d = abs(p - baseline)
            if (d > swing) {
                swing = d
                extremeIndex = i
            }
        }
        val returned = abs(pitch - baseline) <= thresholdDegrees * RETURN_FRACTION
        val extremeInside = extremeIndex in 1 until samples.size - 1
        if (swing >= thresholdDegrees && returned && extremeInside) {
            samples.clear()
            samples.addLast(timestampMs to pitch)
            holdUntil = timestampMs + holdMs
            return Update(fired = true, swing = swing)
        }
        return Update(fired = false, swing = swing)
    }

    fun isHolding(timestampMs: Long) = timestampMs < holdUntil

    fun reset() {
        samples.clear()
        holdUntil = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 800L
        const val RETURN_FRACTION = 0.35f
    }
}
