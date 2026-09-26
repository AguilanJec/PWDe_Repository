package com.pwde.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.pwde.app.PwdeApplication
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.TriggerType
import com.pwde.app.play.GameCommand
import com.pwde.app.play.LivePlay
import com.pwde.app.play.LivePlayState
import com.pwde.app.play.MovementStick
import com.pwde.app.play.ScrollDirection
import com.pwde.app.sensors.face.JoystickDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The "PWDe" entry in Android Settings → Accessibility (its switch reads "Use PWDe"). While a live
 * session runs over the real game ([LivePlay]), it draws the head pointer and the mode bubble over
 * any app and performs the session's screen actions: taps on mapped buttons, select, touch & hold,
 * scroll and drag at the pointer, and Back / Home / Recents / Notifications / All apps. It never
 * reads what's on screen.
 */
class PwdeAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private var cursorView: CursorOverlayView? = null
    private var bubbleView: ModeBubbleView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var captionView: SpeechCaptionView? = null
    private var captionParams: WindowManager.LayoutParams? = null
    private var drag: ContinuousStroke? = null

    /** The finger holding the game's movement joystick, while the head joystick is deflected. */
    private var stick: ContinuousStroke? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val livePlay = (application as PwdeApplication).container.livePlay
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { livePlay.actions.collect { perform(it, livePlay) } }
            s.launch { livePlay.state.collect { render(it, livePlay) } }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        shutDown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutDown()
        super.onDestroy()
    }

    private fun shutDown() {
        scope?.cancel()
        scope = null
        drag?.release()
        drag = null
        stick?.release()
        stick = null
        removeOverlays()
    }

    // ---- Overlays ----

    private fun render(state: LivePlayState, livePlay: LivePlay) {
        if (!state.active) {
            drag?.release()
            drag = null
            stick?.release()
            stick = null
            removeOverlays()
            return
        }
        val face = state.face
        val joystick = face.outputMode == FaceOutputMode.JOYSTICK
        steerStick(state, livePlay)
        if (joystick) {
            removeView(cursorView)
            cursorView = null
        } else {
            val view = cursorView ?: CursorOverlayView(this).takeIf { addOverlay(it, cursorParams()) }?.also { cursorView = it }
            val point = toScreen(face.cursor.x, face.cursor.y)
            view?.update(point.x, point.y, active = face.hasFace && !state.paused, dragging = state.dragging)
        }
        if (state.overlayHidden) {
            removeView(bubbleView)
            bubbleView = null
            removeView(captionView)
            captionView = null
        } else {
            val view = bubbleView ?: createBubble(livePlay)
            view?.update(if (joystick) "Joystick" else "Cursor", state.paused)
            val heard = state.heard
            (captionView ?: createCaption())?.update(state.voiceModel, heard?.text, heard?.matched == true, heard?.seq ?: 0)
        }
    }

    private fun createBubble(livePlay: LivePlay): ModeBubbleView? {
        val params = bubbleParams ?: bubbleLayoutParams().also { bubbleParams = it }
        val view = ModeBubbleView(
            this,
            onTap = { livePlay.request(GameCommand.TogglePause) },
            onLongPress = {
                val joystick = livePlay.state.value.face.outputMode == FaceOutputMode.JOYSTICK
                livePlay.request(if (joystick) GameCommand.CursorMode else GameCommand.JoystickMode)
            },
            onMove = { dx, dy ->
                params.x += dx
                params.y += dy
                bubbleView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                // The caption rides along under the bubble.
                captionParams?.let { caption ->
                    placeCaption(caption, params)
                    captionView?.let { runCatching { windowManager.updateViewLayout(it, caption) } }
                }
            },
        )
        if (!addOverlay(view, params)) return null
        bubbleView = view
        return view
    }

    private fun createCaption(): SpeechCaptionView? {
        val bubble = bubbleParams ?: return null
        val params = captionParams ?: captionLayoutParams().also { captionParams = it }
        placeCaption(params, bubble)
        val view = SpeechCaptionView(this)
        if (!addOverlay(view, params)) return null
        captionView = view
        return view
    }

    /** Just below the bubble, left edges aligned. */
    private fun placeCaption(caption: WindowManager.LayoutParams, bubble: WindowManager.LayoutParams) {
        caption.x = bubble.x
        caption.y = bubble.y + ((ModeBubbleView.SIZE_DP + CAPTION_GAP_DP) * resources.displayMetrics.density).toInt()
    }

    private fun addOverlay(view: View, params: WindowManager.LayoutParams): Boolean =
        runCatching { windowManager.addView(view, params) }
            .onFailure { Log.w(TAG, "Couldn't show the overlay", it) }
            .isSuccess

    private fun removeView(view: View?) {
        if (view != null) runCatching { windowManager.removeView(view) }
    }

    private fun removeOverlays() {
        removeView(cursorView)
        removeView(bubbleView)
        removeView(captionView)
        cursorView = null
        bubbleView = null
        captionView = null
    }

    /** Covers the whole display, cutouts included, and never takes touches. */
    private fun cursorParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    /** Starts at the top-left, below the status bar; the user can drag it anywhere. */
    private fun bubbleLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val density = resources.displayMetrics.density
        x = (16 * density).toInt()
        y = (96 * density).toInt()
    }

    /** Sized to its text and never takes touches, so the game under it stays tappable. */
    private fun captionLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP or Gravity.START }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    // ---- Movement joystick ----

    /**
     * In joystick mode, holds the game's movement joystick (the button marked "Movement") and drags
     * it the way the head joystick points; lets go when the head is back in the dead zone.
     */
    private fun steerStick(state: LivePlayState, livePlay: LivePlay) {
        val face = state.face
        val movement = state.buttons.firstOrNull { it.trigger?.type == TriggerType.MOVEMENT }
        val deflected = movement != null && face.outputMode == FaceOutputMode.JOYSTICK && face.hasFace &&
            !state.paused && face.joystick.direction != JoystickDirection.CENTER
        if (!deflected || movement == null) {
            stick?.release()
            stick = null
            return
        }
        if (stick?.isHeld == true) return
        val center = toScreen(movement.x, movement.y)
        stick = ContinuousStroke(
            this,
            target = {
                val current = livePlay.state.value
                val (width, height) = displaySize()
                val reach = MovementStick.REACH * minOf(width, height)
                val button = current.buttons.firstOrNull { it.trigger?.type == TriggerType.MOVEMENT }
                val c = button?.let { toScreen(it.x, it.y) } ?: center
                PointF(
                    (c.x + current.face.joystick.x * reach).coerceIn(1f, width - 2f),
                    (c.y + current.face.joystick.y * reach).coerceIn(1f, height - 2f),
                )
            },
        ).also { it.press(center) }
    }

    // ---- Actions ----

    private fun perform(command: GameCommand, livePlay: LivePlay) {
        val cursor = livePlay.state.value.face.cursor
        val pointer = toScreen(cursor.x, cursor.y)
        when (command) {
            // Button positions are fractions of a full-screen screenshot from this phone.
            is GameCommand.Press -> tap(toScreen(command.button.x, command.button.y), TAP_MS)
            GameCommand.Select -> {
                tap(pointer, TAP_MS)
                cursorView?.flash()
            }
            GameCommand.TouchHold -> tap(pointer, HOLD_MS)
            is GameCommand.Scroll -> swipe(pointer, command.direction)
            GameCommand.StartDrag -> {
                drag?.release()
                drag = ContinuousStroke(
                    this,
                    target = { livePlay.state.value.face.cursor.let { toScreen(it.x, it.y) } },
                    onCancelled = { livePlay.request(GameCommand.Drop) },
                ).also { it.press(pointer) }
            }
            GameCommand.Drop -> {
                drag?.release()
                drag = null
            }
            GameCommand.Recents -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GameCommand.Back -> performGlobalAction(GLOBAL_ACTION_BACK)
            GameCommand.Home -> performGlobalAction(GLOBAL_ACTION_HOME)
            GameCommand.Notifications -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GameCommand.AllApps -> performGlobalAction(
                if (Build.VERSION.SDK_INT >= 31) GLOBAL_ACTION_ACCESSIBILITY_ALL_APPS else GLOBAL_ACTION_RECENTS,
            )
            // Everything else is handled by the session itself.
            else -> Unit
        }
    }

    private fun tap(point: PointF, durationMs: Long) {
        // A new gesture would lift a held finger, so taps ride along with it instead.
        val held = stick?.takeIf { it.isHeld } ?: drag?.takeIf { it.isHeld }
        if (held != null) {
            Log.d(TAG, "Tap at $point rides on the held ${if (held === stick) "joystick" else "drag"}")
            return held.tap(point, durationMs)
        }
        val path = Path().apply { moveTo(point.x, point.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        if (!dispatchGesture(gesture, null, null)) Log.w(TAG, "Tap at $point was rejected")
    }

    /** Moves the content under the pointer so it scrolls the way [direction] reads. */
    private fun swipe(center: PointF, direction: ScrollDirection) {
        val (width, height) = displaySize()
        val (dx, dy) = when (direction) {
            // Showing what's below means the finger moves up, and so on.
            ScrollDirection.DOWN -> 0f to -SCROLL_FRACTION * height
            ScrollDirection.UP -> 0f to SCROLL_FRACTION * height
            ScrollDirection.RIGHT -> -SCROLL_FRACTION * width to 0f
            ScrollDirection.LEFT -> SCROLL_FRACTION * width to 0f
        }
        val clampX = { v: Float -> v.coerceIn(1f, width - 2f) }
        val clampY = { v: Float -> v.coerceIn(1f, height - 2f) }
        val path = Path().apply {
            moveTo(clampX(center.x - dx / 2), clampY(center.y - dy / 2))
            lineTo(clampX(center.x + dx / 2), clampY(center.y + dy / 2))
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, SCROLL_MS))
            .build()
        if (!dispatchGesture(gesture, null, null)) Log.w(TAG, "Scroll $direction was rejected")
    }

    /** A 0–1 position to pixels on the whole display, as it's rotated right now. */
    private fun toScreen(x: Float, y: Float): PointF {
        val (width, height) = displaySize()
        val (px, py) = ScreenMapping.toPixels(x, y, width, height)
        return PointF(px, py)
    }

    private fun displaySize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    companion object {
        private const val TAG = "PwdeAccessibility"
        private const val TAP_MS = 60L
        private const val HOLD_MS = 700L
        private const val SCROLL_MS = 300L
        private const val CAPTION_GAP_DP = 6

        /** How far one "scroll" moves, as a share of the screen. */
        private const val SCROLL_FRACTION = 0.4f

        /** True when the user has switched "Use PWDe" on in Android's accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val self = ComponentName(context, PwdeAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == self }
        }

        /** Opens Android's accessibility settings, where PWDe is listed. */
        fun settingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** Normalized (0–1) screen positions to pixels. Pure, so it's unit-tested. */
object ScreenMapping {
    fun toPixels(x: Float, y: Float, width: Int, height: Int): Pair<Float, Float> {
        // Stay a pixel inside the screen: a gesture on the very edge is rejected.
        val px = (x.coerceIn(0f, 1f) * width).coerceIn(0f, (width - 1).toFloat())
        val py = (y.coerceIn(0f, 1f) * height).coerceIn(0f, (height - 1).toFloat())
        return px to py
    }
}
