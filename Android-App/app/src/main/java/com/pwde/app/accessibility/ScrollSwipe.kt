package com.pwde.app.accessibility

import com.pwde.app.play.ScrollDirection

/**
 * Where a "scroll" puts its finger and how it travels, in display pixels. The content moves the way
 * [ScrollDirection] reads, so showing what's below means the finger moves up.
 *
 * Pure floats on purpose (no `PointF`), so it unit-tests on the JVM like [ScreenMapping].
 */
object ScrollSwipe {
    /** How far one "scroll" moves, as a share of the screen. */
    const val FRACTION = 0.4f

    /** The finger's start and end, both kept a pixel inside the screen: a gesture on the edge is rejected. */
    data class Travel(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    fun travel(centerX: Float, centerY: Float, direction: ScrollDirection, width: Int, height: Int): Travel {
        val (dx, dy) = when (direction) {
            ScrollDirection.DOWN -> 0f to -FRACTION * height
            ScrollDirection.UP -> 0f to FRACTION * height
            ScrollDirection.RIGHT -> -FRACTION * width to 0f
            ScrollDirection.LEFT -> FRACTION * width to 0f
        }
        return Travel(
            fromX = clamp(centerX - dx / 2, width),
            fromY = clamp(centerY - dy / 2, height),
            toX = clamp(centerX + dx / 2, width),
            toY = clamp(centerY + dy / 2, height),
        )
    }

    /** Where the finger is [elapsedMs] into a [durationMs] travel. It stays at the end once it arrives. */
    fun pointAt(travel: Travel, elapsedMs: Long, durationMs: Long): Pair<Float, Float> {
        val fraction = if (durationMs <= 0L) 1f else (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
        return (travel.fromX + (travel.toX - travel.fromX) * fraction) to
            (travel.fromY + (travel.toY - travel.fromY) * fraction)
    }

    private fun clamp(value: Float, size: Int) = value.coerceIn(1f, (size - 2).toFloat())
}
