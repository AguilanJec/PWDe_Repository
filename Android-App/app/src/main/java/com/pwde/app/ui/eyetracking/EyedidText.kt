package com.pwde.app.ui.eyetracking

import com.pwde.app.sensors.eyedid.EyedidStatus

/**
 * The words for every state the gaze SDK can be in, in one place so the diagnostics rig and the
 * calibration screen cannot drift apart — a licence failure explained two different ways is two
 * different bugs to the person reading it.
 */
object EyedidText {

    /** Short label for a status pill. */
    fun label(status: EyedidStatus): String = when (status) {
        EyedidStatus.Idle -> "not started"
        EyedidStatus.Initializing -> "checking licence…"
        EyedidStatus.Ready -> "ready"
        EyedidStatus.Tracking -> "tracking"
        EyedidStatus.NoLicenceKey -> "no licence key"
        is EyedidStatus.Failed -> "unavailable"
        is EyedidStatus.Interrupted -> "interrupted"
    }

    /**
     * What is wrong and what to do about it, or null when nothing is wrong. The SDK's own enum names
     * are unreadable, so the engine has already turned each one into a sentence; this only adds the
     * cases the engine cannot know about.
     */
    fun advice(status: EyedidStatus): String? = when (status) {
        is EyedidStatus.Failed -> status.advice
        is EyedidStatus.Interrupted -> status.advice
        EyedidStatus.NoLicenceKey ->
            "Eye control is not set up in this build, so it cannot be used. Everything else works as usual."
        EyedidStatus.Idle, EyedidStatus.Initializing, EyedidStatus.Ready, EyedidStatus.Tracking -> null
    }

    /** Extra detail for developers: the diagnostics rig shows this, the user's screens do not. */
    fun developerAdvice(status: EyedidStatus): String? = when (status) {
        EyedidStatus.NoLicenceKey ->
            "Add pwde.eyedid.licenseKey=… to Android-App/local.properties, re-sync Gradle and reinstall."
        else -> null
    }
}
