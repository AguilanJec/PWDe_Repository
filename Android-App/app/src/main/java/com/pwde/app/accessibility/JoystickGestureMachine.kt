package com.pwde.app.accessibility

import kotlin.math.max

/**
 * Which stroke the movement stick should dispatch next, tick by tick. Pure, so the behaviour that
 * matters is tested on the JVM ([JoystickGestureMachineTest]).
 *
 * Ported from the reference implementation's `JoystickGestureMachine` (a Google Project GameFace
 * fork), because it is the part that makes a held stick behave: a deflected stick reads to the game
 * as **one continuous finger press** — down at the base, out to the deflection, held, then lifted —
 * rather than a fresh short stroke every frame, which a game experiences as a stream of quick taps.
 *
 * Two of its rules are the anti-jitter mechanism, and they are why this replaced a hand-rolled
 * release grace:
 * - **Holding still dispatches nothing.** Once pressed, a stroke is only dispatched when the target
 *   has actually moved by `minMovePx` *and* `minIntervalMs` has passed since the last one.
 * - **`minMovePx` is a share of the stick's own radius** (`moveStep`), so tremor smaller than that
 *   never moves the finger at all. That is much stronger than damping, which only shrinks jitter.
 *
 * @param releaseGraceMs how long the stick may read as centered before the finger is lifted, which
 *   absorbs a momentary dip back into the dead zone instead of dropping the stick to the middle.
 * @param minIntervalMs minimum time between two dispatched strokes, to avoid flooding the queue.
 */
class JoystickGestureMachine(
    releaseGraceMs: Float,
    minIntervalMs: Long,
) {
    /** The kind of stroke to dispatch next. */
    enum class Kind {
        /** Finger goes down at the stick's base and drags out to the deflection. */
        PRESS_DRAG,

        /** The already-held finger drags from where it is to a new target. */
        DRAG,

        /** The held finger lifts; only `toX`/`toY` (where it is held) matter. */
        RELEASE,
    }

    /** One decision: `kind`, where the finger is (`fromX`/`fromY`) and where it should end up. */
    class Step(
        val kind: Kind,
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float,
    ) {
        companion object {
            internal fun pressDrag(baseX: Float, baseY: Float, targetX: Float, targetY: Float) =
                Step(Kind.PRESS_DRAG, baseX, baseY, targetX, targetY)

            internal fun drag(fromX: Float, fromY: Float, toX: Float, toY: Float) =
                Step(Kind.DRAG, fromX, fromY, toX, toY)

            internal fun release(x: Float, y: Float) = Step(Kind.RELEASE, x, y, x, y)
        }
    }

    private enum class Phase { IDLE, PRESSED }

    private val releaseGraceMs: Float = max(0f, releaseGraceMs)

    /** Minimum spacing between two dispatched strokes, to avoid flooding the event queue. */
    private val minIntervalMs: Long = max(0L, minIntervalMs)

    private var phase = Phase.IDLE
    private var fingerX = 0f
    private var fingerY = 0f
    private var lastTargetX = 0f
    private var lastTargetY = 0f
    private var lastDispatchAtMs = Long.MIN_VALUE / 2
    private var deflectionLostAtMs = -1L

    /** Whether the machine currently believes a finger is held down. */
    fun isPressed(): Boolean = phase == Phase.PRESSED

    /**
     * Advance the machine for one tick.
     *
     * @param nowMs monotonic time of this tick.
     * @param deflected whether the stick is currently past its dead zone.
     * @param baseX/baseY screen position of the stick's base, where the finger first goes down.
     * @param targetX/targetY screen position the thumb is at for the current deflection.
     * @param minMovePx smallest target movement that justifies a new drag.
     * @return the stroke to dispatch, or null when nothing should be dispatched this tick.
     */
    fun update(
        nowMs: Long,
        deflected: Boolean,
        baseX: Float,
        baseY: Float,
        targetX: Float,
        targetY: Float,
        minMovePx: Float,
    ): Step? {
        if (deflected) {
            deflectionLostAtMs = -1L
            if (phase == Phase.IDLE) {
                // Deflection onset: down at the base and out to the target in one stroke.
                phase = Phase.PRESSED
                lastTargetX = targetX
                lastTargetY = targetY
                lastDispatchAtMs = nowMs
                // The next continuation begins where this stroke ends, i.e. at the target.
                fingerX = targetX
                fingerY = targetY
                return Step.pressDrag(baseX, baseY, targetX, targetY)
            }
            // Already held: only dispatch when the target really moved and enough time passed, so
            // holding still never produces a stream of strokes.
            if (nowMs - lastDispatchAtMs >= minIntervalMs && movedEnough(targetX, targetY, minMovePx)) {
                val step = Step.drag(fingerX, fingerY, targetX, targetY)
                fingerX = targetX
                fingerY = targetY
                lastTargetX = targetX
                lastTargetY = targetY
                lastDispatchAtMs = nowMs
                return step
            }
            return null
        }

        if (phase == Phase.IDLE) return null
        if (deflectionLostAtMs < 0L) deflectionLostAtMs = nowMs
        if (nowMs - deflectionLostAtMs >= releaseGraceMs) {
            // Grace expired: lift exactly where the finger is held, with no new press.
            phase = Phase.IDLE
            deflectionLostAtMs = -1L
            return Step.release(fingerX, fingerY)
        }
        // Still inside the grace window: keep holding in place.
        return null
    }

    /**
     * Lift the finger now, ignoring the grace timer, without waiting for the next [update]. Used when
     * the mode changes or the service is torn down mid-hold.
     *
     * A release is only *reported* here; the caller still has to dispatch it, and must not then
     * dispatch anything else until it has.
     */
    fun forceRelease(): Step? {
        if (phase != Phase.PRESSED) return null
        phase = Phase.IDLE
        deflectionLostAtMs = -1L
        return Step.release(fingerX, fingerY)
    }

    /** Drop all state without emitting any stroke, e.g. on teardown. */
    fun reset() {
        phase = Phase.IDLE
        deflectionLostAtMs = -1L
        lastDispatchAtMs = Long.MIN_VALUE / 2
    }

    private fun movedEnough(targetX: Float, targetY: Float, minMovePx: Float): Boolean {
        val dx = targetX - lastTargetX
        val dy = targetY - lastTargetY
        return dx * dx + dy * dy >= minMovePx * minMovePx
    }
}
