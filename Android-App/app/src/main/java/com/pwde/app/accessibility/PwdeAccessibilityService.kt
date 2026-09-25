package com.pwde.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * The "PWDe" entry in Android Settings → Accessibility (its switch reads "Use PWDe").
 * Setup step B6 asks the user to turn it on. It does nothing yet: acting on other apps
 * (system-wide gestures, taps outside PWDe's overlay) is planned for a later update.
 */
class PwdeAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
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
