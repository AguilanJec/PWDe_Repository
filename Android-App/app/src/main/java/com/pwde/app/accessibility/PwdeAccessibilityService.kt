package com.pwde.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PointF
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.pwde.app.PwdeApplication
import com.pwde.app.play.GameCommand
import com.pwde.app.play.LivePlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The "PWDe" entry in Android Settings → Accessibility (its switch reads "Use PWDe"). While a live
 * session runs over the real game ([LivePlay]), it performs that session's screen actions: taps on
 * mapped buttons, select and touch & hold at the head pointer, and Back / Home / Notifications /
 * All apps. It never reads what's on screen.
 */
class PwdeAccessibilityService : AccessibilityService() {
    private var scope: CoroutineScope? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        val livePlay = (application as PwdeApplication).container.livePlay
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { s ->
            s.launch { livePlay.actions.collect { perform(it, livePlay) } }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        scope?.cancel()
        scope = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    private fun perform(command: GameCommand, livePlay: LivePlay) {
        val cursor = livePlay.state.value.face.cursor
        when (command) {
            // Button positions are fractions of a full-screen screenshot from this phone.
            is GameCommand.Press -> tap(toScreen(command.button.x, command.button.y), TAP_MS)
            GameCommand.Select -> tap(toScreen(cursor.x, cursor.y), TAP_MS)
            GameCommand.TouchHold -> tap(toScreen(cursor.x, cursor.y), HOLD_MS)
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
        val path = Path().apply { moveTo(point.x, point.y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        if (!dispatchGesture(gesture, null, null)) Log.w(TAG, "Tap at $point was rejected")
    }

    /** A 0–1 position to pixels on the whole display, as it's rotated right now. */
    private fun toScreen(x: Float, y: Float): PointF {
        val (width, height) = displaySize()
        val (px, py) = ScreenMapping.toPixels(x, y, width, height)
        return PointF(px, py)
    }

    private fun displaySize(): Pair<Int, Int> {
        val windowManager = getSystemService(WindowManager::class.java)
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
