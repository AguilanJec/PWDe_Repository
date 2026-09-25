package com.pwde.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF

/**
 * One finger held on the screen and moved in short segments toward wherever [target] says, until
 * [release]. Built from continued gesture strokes (Android 8+), each dispatched when the previous
 * one completes. Used for drag-and-drop now and the joystick hold next.
 */
class ContinuousStroke(
    private val service: AccessibilityService,
    /** Where the finger should be now, in display pixels. */
    private val target: () -> PointF,
    /** The system cancelled the stroke (e.g. another gesture or the screen turning off). */
    private val onCancelled: () -> Unit = {},
) {
    private var last: GestureDescription.StrokeDescription? = null
    private var lastPoint = PointF()
    private var releasing = false

    val isHeld: Boolean get() = last != null

    /** Put the finger down at [start]. */
    fun press(start: PointF) {
        releasing = false
        lastPoint = start
        val stroke = GestureDescription.StrokeDescription(Path().apply { moveTo(start.x, start.y) }, 0, SEGMENT_MS, true)
        last = stroke
        dispatch(stroke)
    }

    /** Lift the finger after one last move. */
    fun release() {
        if (last != null) releasing = true
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
        dispatch(stroke)
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (stroke.willContinue()) next()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (last == null) return
                last = null
                onCancelled()
            }
        }, null)
        if (!accepted) {
            last = null
            onCancelled()
        }
    }

    private companion object {
        /** Short enough to follow the head smoothly, long enough not to flood the system. */
        const val SEGMENT_MS = 80L
    }
}
