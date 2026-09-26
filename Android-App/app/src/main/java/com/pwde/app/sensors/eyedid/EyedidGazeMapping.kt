package com.pwde.app.sensors.eyedid

import com.pwde.app.sensors.face.GazeDwell
import kotlin.math.sqrt

/**
 * A point in a 2-D space. Which space — device-screen pixels, or a fraction of a box — is the
 * caller's business; everything here is unit-agnostic and only ever mixes points from one space.
 *
 * The SDK reports in device-screen pixels. The UI draws in fractions of a box it has measured. Both
 * are this type, and [EyedidGazeMapping.toBoxFraction] is the one place they meet.
 */
data class GazePoint(val x: Float, val y: Float)

/**
 * Why there is no dot on screen. Every value is a different thing to tell the user — "no face" and
 * "eyes lost" look identical on a blank preview but have completely different fixes.
 */
enum class GazeLostReason {
    /** The engine has not been started, or was stopped. */
    NOT_TRACKING,

    /** The SDK sees no face at all. */
    NO_FACE,

    /** The face is there but the eyes were unreadable for longer than the hold window. */
    EYES_LOST,

    /** The drawing surface has not been laid out yet, so there is nothing to map onto. */
    SCREEN_NOT_MEASURED,
}

/**
 * Pure conversions and smoothing for the SeeSo/Eyedid gaze stream. No SDK and no Android types, so
 * all of it is unit-testable without a device.
 */
object EyedidGazeMapping {

    /**
     * Device-screen pixels -> fraction of a box placed at ([boxLeftPx], [boxTopPx]) on that screen.
     *
     * Returns null rather than dividing by zero before the box has been measured: a NaN point
     * silently draws nowhere and looks exactly like a broken tracker.
     *
     * Points past the edge are pinned to it, not dropped. The SDK reports points off-screen when the
     * user looks at the bezel, and dropping those makes the dot flicker at the extremes — the very
     * place a user notices it.
     *
     * Pass a zero origin to map a box that is the whole screen.
     */
    fun toBoxFraction(
        x: Float,
        y: Float,
        boxLeftPx: Float,
        boxTopPx: Float,
        boxWidthPx: Int,
        boxHeightPx: Int,
    ): GazePoint? {
        if (boxWidthPx <= 0 || boxHeightPx <= 0) return null
        if (!x.isFinite() || !y.isFinite()) return null
        return GazePoint(
            ((x - boxLeftPx) / boxWidthPx).coerceIn(0f, 1f),
            ((y - boxTopPx) / boxHeightPx).coerceIn(0f, 1f),
        )
    }
}

/**
 * Turns the SDK's per-frame readings into the single dot the UI draws, in whatever space the
 * readings arrive in (the engine passes device-screen pixels straight through).
 *
 * Two jobs, and both exist because the naive version looked broken on a real device:
 *
 * **Smoothing.** Raw readings jitter by several pixels, so the dot is eased toward each new reading
 * instead of jumping. The *first* reading is taken exactly — otherwise the dot slides in from
 * wherever it was, and the user reads that as lag.
 *
 * **Holding.** A blink is a few frames with no gaze at all. Releasing the dot on every blink makes
 * it strobe, so a short loss keeps the last point. A longer loss, or the face leaving the frame,
 * releases it — a dot left behind after the user looks away is worse than no dot, because it looks
 * like eye tracking that works.
 *
 * Not thread-safe: call it from one thread (the ViewModel does, on the main dispatcher).
 */
class EyedidPointer(
    private val smoothing: Float = DEFAULT_SMOOTHING,
    private val holdMs: Long = DEFAULT_HOLD_MS,
) {
    /** Where to draw, or null when nothing should be drawn. */
    var point: GazePoint? = null
        private set

    /** Non-null exactly when [point] is null, and says what is missing. */
    var reason: GazeLostReason? = GazeLostReason.NOT_TRACKING
        private set

    private var lastSeenMs = 0L

    /** Tracking has begun; nothing is drawn until the first usable reading arrives. */
    fun trackingStarted() {
        point = null
        reason = GazeLostReason.NO_FACE
    }

    fun trackingStopped() {
        point = null
        reason = GazeLostReason.NOT_TRACKING
    }

    /**
     * One frame.
     *
     * @param reading the gaze point, or null when this frame has no usable gaze.
     * @param faceVisible whether the SDK still sees a face, which is what distinguishes a blink
     *   (hold the dot) from the user getting up (drop it).
     * @param nowMs the frame timestamp. Must be the same clock across calls — these are only ever
     *   compared against each other, so the SDK's own frame timestamps are the right ones.
     */
    fun update(reading: GazePoint?, faceVisible: Boolean, nowMs: Long): GazePoint? {
        if (reading != null) {
            lastSeenMs = nowMs
            reason = null
            // First reading is taken as-is; later ones are eased toward.
            point = point?.let { smooth(it, reading) } ?: reading
            return point
        }

        if (!faceVisible) {
            // The face is gone, so any dot still on screen would be stale.
            point = null
            reason = GazeLostReason.NO_FACE
            return null
        }

        // No gaze but the face is there: a blink, or the eyes simply are not readable.
        val held = point
        if (held != null && nowMs - lastSeenMs <= holdMs) return held

        point = null
        reason = GazeLostReason.EYES_LOST
        return null
    }

    private fun smooth(from: GazePoint, to: GazePoint) = GazePoint(
        from.x + (to.x - from.x) * smoothing,
        from.y + (to.y - from.y) * smoothing,
    )

    companion object {
        /** Fraction of the gap to the new reading taken per frame. Higher = snappier, more jitter. */
        const val DEFAULT_SMOOTHING = 0.35f

        /** How long a blink may last before the pointer is released. */
        const val DEFAULT_HOLD_MS = 400L
    }
}

/**
 * Watches the smoothed gaze and reports when it has rested on one spot long enough to be a press —
 * the "look at a button and hold" gesture, since a user with no hand control has no other way to
 * click.
 *
 * Three rules, each of which the naive version gets wrong:
 *
 * **A press needs the gaze to *stay*.** Progress accumulates only while the point remains inside
 * [radius] of where the dwell started. Drifting across the screen restarts it, so merely looking
 * around never presses anything.
 *
 * **A press is an event, not a state.** It fires once per visit and then does not fire again until
 * the gaze leaves [radius] and comes back. Firing on "progress is full" every frame would press the
 * button sixty times a second.
 *
 * **[rearmMs] stops a jittery gaze machine-gunning.** Sitting exactly on the edge of [radius] would
 * otherwise release and re-arm the moment the eyes wobble a pixel, and press twice for one look.
 * A short re-arm window means a real "look again" still works (a person cannot re-fixate in under
 * a quarter second) but tremor cannot fake one.
 *
 * Coordinates are whatever the caller's [update] points are — the manager feeds it screen fractions,
 * so [radius] and the button hit tolerance are directly comparable. Not thread-safe.
 */
class EyedidDwellTracker(
    private val holdMs: Long = DEFAULT_HOLD_MS,
    private val radius: Float = DEFAULT_RADIUS,
    private val rearmMs: Long = DEFAULT_REARM_MS,
) {
    /** 0..1 toward firing, at the spot the dwell started on. 0 when nothing is resting. */
    var progress: Float = 0f
        private set

    /** Where the current dwell began, or null when the gaze is not resting anywhere. */
    var target: GazePoint? = null
        private set

    /** Start of the current run of stillness. */
    private var startMs = 0L

    /** Set once a dwell has fired, so the same visit cannot fire twice. */
    private var fired = false

    /** When the last press fired, for [rearmMs]. Null until the first one. */
    private var lastFireMs: Long? = null

    /** A lost gaze cannot be resting on anything. */
    fun reset() {
        progress = 0f
        target = null
        fired = false
    }

    /**
     * One frame.
     *
     * Two arguments rather than one, because "there is no reading this frame" has two completely
     * different meanings and only the pointer knows which one it is:
     *
     * - [reading] is null but [held] is not: **a blink**. The ring stays exactly where it was, and
     *   the hold is neither restarted nor advanced. Restarting it here would mean a press could
     *   almost never complete, since people blink constantly; the clock does keep running, so a
     *   blink cannot delay a press the user had already earned. (A held point is only ever returned
     *   through a short loss, so this cannot become "holding a target with the eyes shut".)
     * - both null: **the gaze is gone**. Nothing can be resting anywhere, so a part-finished hold
     *   means nothing and is discarded.
     *
     * @param nowMs the frame timestamp, on the same clock as every other call.
     * @return the press this frame produced, or null for the far more common "not yet".
     */
    fun frame(reading: GazePoint?, held: GazePoint?, nowMs: Long): GazeDwell? = when {
        reading != null -> held?.let { update(it, nowMs) }
        held != null -> null
        else -> {
            reset()
            null
        }
    }

    /**
     * One readable frame. Normally reached through [frame], which is what decides whether there is
     * a point to pass in the first place.
     */
    private fun update(point: GazePoint, nowMs: Long): GazeDwell? {
        val anchor = target
        if (anchor == null || distance(point, anchor) > radius) {
            // Arrived somewhere new (or moved enough to be somewhere new): start counting from here.
            target = point
            startMs = nowMs
            progress = 0f
            fired = false
            return null
        }

        if (fired) {
            // Still on the same spot, already pressed: hold the ring full and wait for the user to
            // look away. Re-pressing first requires leaving and coming back.
            progress = 1f
            return null
        }

        progress = ((nowMs - startMs).toFloat() / holdMs).coerceIn(0f, 1f)
        if (progress < 1f) return null
        // Null until the first press, rather than a "very old" sentinel: subtracting Long.MIN_VALUE
        // overflows, and the sign of the overflow would suppress that first press entirely.
        if (lastFireMs?.let { nowMs - it < rearmMs } == true) return null

        fired = true
        lastFireMs = nowMs
        return GazeDwell(anchor.x, anchor.y)
    }

    private fun distance(a: GazePoint, b: GazePoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    companion object {
        /**
         * How long the gaze must rest before it presses. Short enough not to feel like waiting,
         * long enough that a glance on the way to somewhere else cannot press by accident.
         */
        const val DEFAULT_HOLD_MS = 800L

        /**
         * How far the gaze may drift and still count as resting on the same spot, as a fraction of
         * the screen. Kept well inside `GameInput.GAZE_HIT_TOLERANCE` so a settled gaze is always
         * still within the button it settled on.
         */
        const val DEFAULT_RADIUS = 0.04f

        /** Shortest gap between two presses, so tremor at the edge of the radius cannot double-fire. */
        const val DEFAULT_REARM_MS = 250L
    }
}
