package com.pwde.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.util.Log

/**
 * One finger held on the screen and moved in short segments toward wherever [target] says, until
 * [release]. Built from continued gesture strokes (Android 8+), each dispatched when the previous
 * one completes. Used for drag-and-drop and for holding the game's movement joystick.
 *
 * Any new gesture cancels one in progress, so while this finger is down other taps must go
 * through [tap]: they ride along in the next segment as extra fingers.
 */
class ContinuousStroke(
    private val service: AccessibilityService,
    /** Where the finger should be now, in display pixels. */
    private val target: () -> PointF,
    /** The system cancelled the stroke (e.g. the screen turned off). */
    private val onCancelled: () -> Unit = {},
    /**
     * Taps were sent on their own, lasting up to the given milliseconds. Nothing may press a new
     * finger until then, or that press would cancel them.
     */
    private val onTapsResent: (durationMs: Long) -> Unit = {},
) {
    private var last: GestureDescription.StrokeDescription? = null
    private var lastPoint = PointF()
    private var releasing = false
    private val pendingTaps = mutableListOf<GestureDescription.StrokeDescription>()

    val isHeld: Boolean get() = last != null

    /** Put the finger down at [start]. */
    fun press(start: PointF) {
        releasing = false
        lastPoint = start
        val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(start.x, start.y) }, 0, SEGMENT_MS, true)
        last = stroke
        dispatch(stroke, emptyList())
    }

    /** Lift the finger after one last move. */
    fun release() {
        if (last != null) releasing = true
    }

    /** A tap by another finger, sent with the next segment so the held finger stays down. */
    fun tap(point: PointF, durationMs: Long) {
        val path = Path().apply { moveTo(point.x, point.y) }
        pendingTaps += GestureDescription.StrokeDescription(path, 0, durationMs)
    }

    private fun next() {
        val previous = last ?: return
        val to = target()
        val path = Path().apply {
            moveTo(lastPoint.x, lastPoint.y)
            lineTo(to.x, to.y)
        }
        lastPoint = to
        val lift = releasing
        val stroke = previous.continueStroke(path, 0, SEGMENT_MS, !lift)
        last = if (lift) null else stroke
        releasing = false
        val taps = pendingTaps.take(MAX_EXTRA_FINGERS)
        pendingTaps.clear()
        dispatch(stroke, taps)
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription, taps: List<GestureDescription.StrokeDescription>) {
        if (taps.isNotEmpty()) Log.d(TAG, "Segment with ${taps.size} extra finger(s), continues=${stroke.willContinue()}")
        val gesture = GestureDescription.Builder().addStroke(stroke).apply { taps.forEach(::addStroke) }.build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (stroke.willContinue()) next()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Segment cancelled (held=${last != null}, extra fingers=${taps.size})")
                resendAlone(taps)
                if (last == null) return
                last = null
                onCancelled()
            }
        }, null)
        if (!accepted) {
            Log.w(TAG, "Segment rejected (extra fingers=${taps.size})")
            resendAlone(taps)
            last = null
            onCancelled()
        }
    }

    /**
     * Taps riding on a segment that the system cancelled or rejected would be lost with it (seen on
     * real devices, where held segments are cancelled every few frames), so they go out on their own.
     * The held finger lifts; its owner presses again on the next frame.
     */
    private fun resendAlone(taps: List<GestureDescription.StrokeDescription>) {
        if (taps.isEmpty()) return
        onTapsResent(taps.maxOf { it.duration })
        val gesture = GestureDescription.Builder().apply { taps.forEach(::addStroke) }.build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "Resent ${taps.size} tap(s) on their own: completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Resent ${taps.size} tap(s) on their own: cancelled")
            }
        }, null)
        if (!accepted) Log.w(TAG, "Resent ${taps.size} tap(s) on their own: rejected")
    }

    private companion object {
        const val TAG = "PwdeStroke"

        /** Short enough to follow the head smoothly, long enough not to flood the system. */
        const val SEGMENT_MS = 80L

        /** Taps that can share one segment; Android allows 10+ fingers in total. */
        const val MAX_EXTRA_FINGERS = 4
    }
}
