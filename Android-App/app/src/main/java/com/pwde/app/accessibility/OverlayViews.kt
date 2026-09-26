package com.pwde.app.accessibility

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import kotlin.math.abs

private const val PRIMARY = 0xFF9BEEE2.toInt()
private const val WARNING = 0xFFFFC764.toInt()
private const val DARK = 0xCC0E1A18.toInt()
private const val MUTED = 0xFFB8C9C6.toInt()

/**
 * Full-screen, untouchable layer that draws the head pointer over any app. Taps pass straight
 * through it to the app underneath.
 */
class CursorOverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * density; color = Color.WHITE }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000 }
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f * density }
    private val location = IntArray(2)

    /** Pointer position in display pixels. */
    private var px = 0f
    private var py = 0f
    private var active = true
    private var dragging = false
    private var ripple = 1f
    private val rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 450
        addUpdateListener { ripple = it.animatedValue as Float; invalidate() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun update(x: Float, y: Float, active: Boolean, dragging: Boolean) {
        px = x
        py = y
        this.active = active
        this.dragging = dragging
        invalidate()
    }

    /** A ring where "select" just tapped. */
    fun flash() = rippleAnimator.start()

    override fun onDraw(canvas: Canvas) {
        // The window may not start at the display's corner (cutouts, bars); draw in display coordinates.
        getLocationOnScreen(location)
        val cx = px - location[0]
        val cy = py - location[1]
        val color = when {
            dragging -> WARNING
            active -> PRIMARY
            else -> Color.GRAY
        }
        if (ripple < 1f) {
            ripplePaint.color = color
            ripplePaint.alpha = ((1f - ripple) * 255).toInt()
            canvas.drawCircle(cx, cy, (24f + 50f * ripple) * density, ripplePaint)
        }
        canvas.drawCircle(cx, cy, 16f * density, shadow)
        fill.color = color
        canvas.drawCircle(cx, cy, 12f * density, fill)
        canvas.drawCircle(cx, cy, 12f * density, ring)
    }
}

/**
 * The small floating bubble: shows cursor or joystick mode and whether PWDe is paused. Drag to
 * move it; tap to pause or resume; long-press to switch mode.
 */
@SuppressLint("ViewConstructor")
class ModeBubbleView(
    context: Context,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onMove: (dx: Int, dy: Int) -> Unit,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val size = (SIZE_DP * density).toInt()
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DARK }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * density }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
        isFakeBoldText = true
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var label = "Cursor"
    private var paused = false

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var downAt = 0L
    private var moved = false

    init {
        isClickable = true
        isLongClickable = true
        updateDescription()
    }

    fun update(label: String, paused: Boolean) {
        if (this.label == label && this.paused == paused) return
        this.label = label
        this.paused = paused
        updateDescription()
        invalidate()
    }

    private fun updateDescription() {
        contentDescription = "PWDe, $label mode${if (paused) ", paused" else ""}. Tap to ${if (paused) "resume" else "pause"}, long-press to switch mode."
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = setMeasuredDimension(size, size)

    override fun onDraw(canvas: Canvas) {
        val r = size / 2f
        canvas.drawCircle(r, r, r - 2 * density, background)
        border.color = if (paused) WARNING else PRIMARY
        canvas.drawCircle(r, r, r - 3 * density, border)
        val baseline = r - (text.descent() + text.ascent()) / 2
        canvas.drawText(if (paused) "Paused" else label, r, baseline, text)
    }

    // Tap and long-press also come through performClick/performLongClick for TalkBack and switch users.
    override fun performClick(): Boolean {
        super.performClick()
        onTap()
        return true
    }

    override fun performLongClick(): Boolean {
        super.performLongClick()
        onLongPress()
        return true
    }

    @SuppressLint("ClickableViewAccessibility") // performClick/performLongClick are called below
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX; downY = event.rawY
                lastX = downX; lastY = downY
                downAt = SystemClock.uptimeMillis()
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop)) moved = true
                if (moved) {
                    onMove((event.rawX - lastX).toInt(), (event.rawY - lastY).toInt())
                    lastX = event.rawX; lastY = event.rawY
                }
            }
            MotionEvent.ACTION_UP -> if (!moved) {
                if (SystemClock.uptimeMillis() - downAt >= ViewConfiguration.getLongPressTimeout()) performLongClick() else performClick()
            }
        }
        return true
    }

    companion object {
        const val SIZE_DP = 64
    }
}

/**
 * The caption under the floating bubble: what the in-game speech engine last heard, and which engine
 * it is, so it's plain whether buttons go through sherpa-onnx or the platform recognizer. Touches
 * pass straight through it.
 */
class SpeechCaptionView(context: Context) : TextView(context) {
    private val density = resources.displayMetrics.density
    private val frame = GradientDrawable().apply {
        setColor(DARK)
        cornerRadius = 12 * density
    }
    private var lastSeq = -1
    private val settle = Runnable { frame.setStroke((1.5f * density).toInt(), MUTED) }

    init {
        background = frame
        frame.setStroke((1.5f * density).toInt(), MUTED)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val pad = (8 * density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        maxWidth = (220 * density).toInt()
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        // The session's own feedback already reaches TalkBack; don't read this a second time.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /**
     * [heard] is null until the first result. A new [seq] briefly lights the border: teal for a
     * matched command, amber for speech that matched nothing.
     */
    fun update(model: String?, heard: String?, matched: Boolean, seq: Int) {
        val said = if (heard == null) "Listening…" else "“$heard”" + if (matched) "" else "  (no match)"
        text = SpannableStringBuilder(said).append("\n")
            .append(model ?: "Unknown engine", ForegroundColorSpan(MUTED), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (heard != null && seq != lastSeq) {
            lastSeq = seq
            frame.setStroke((2.5f * density).toInt(), if (matched) PRIMARY else WARNING)
            removeCallbacks(settle)
            postDelayed(settle, HIGHLIGHT_MS)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(settle)
        super.onDetachedFromWindow()
    }

    private companion object {
        const val HIGHLIGHT_MS = 800L
    }
}
