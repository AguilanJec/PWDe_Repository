package com.pwde.app.data.model

/** Static catalog of facial gestures PWDe will recognise once tracking lands in Prompt 2. */
enum class FacialGesture(val label: String, val description: String) {
    SMILE("Smile", "A wide smile"),
    FROWN("Frown", "Pull the corners of your mouth down"),
    OPEN_MOUTH("Open mouth", "Open your mouth, then close it"),
    EYEBROW_RAISE("Eyebrow raise", "Raise both eyebrows"),
    TILT_LEFT("Tilt left", "Tilt your head to the left"),
    TILT_RIGHT("Tilt right", "Tilt your head to the right"),
    NOD("Nod", "A small nod down and back up"),
    WINK("Wink", "Close one eye briefly"),
}

/** Things a user can trigger with a gesture. */
enum class GestureAction(val label: String) {
    SELECT("Select"),
    HOME("Home"),
    BACK("Back"),
    NOTIFICATIONS("Notifications"),
    PAUSE_RESUME("Pause / resume"),
    RECENTER("Recenter"),
    TOUCH_HOLD("Touch & hold"),
    ALL_APPS("All apps"),
}

enum class VoiceMatchMode(val label: String, val description: String) {
    EXACT("Match: exact phrase", "Only \"attack\" by itself"),
    WORD_ANYWHERE("Match: word anywhere", "\"go attack now\" also works"),
}

enum class VoiceActivationMode(val label: String, val description: String) {
    IMMEDIATE("Act: right away", "Faster, can't double-check"),
    AFTER_FINISH("Act: after I finish", "Slower, fewer mistakes"),
}

/** Spoken shortcuts the user can customise. */
enum class VoiceShortcut(val label: String, val defaultPhrase: String) {
    SWITCH_PROFILE("Switch profiles", "switch profile"),
    CURSOR_MODE("Cursor mode", "cursor mode"),
    JOYSTICK_MODE("Joystick mode", "joystick mode"),
}

/** The user's working controls configuration (not yet saved as a named profile). */
data class ControlConfig(
    val gestureAssignments: Map<GestureAction, FacialGesture> = emptyMap(),
    val voiceEnabled: Boolean = true,
    val voiceMatchMode: VoiceMatchMode = VoiceMatchMode.WORD_ANYWHERE,
    val voiceActivationMode: VoiceActivationMode = VoiceActivationMode.IMMEDIATE,
    val voiceShortcuts: Map<VoiceShortcut, String> = VoiceShortcut.entries.associateWith { it.defaultPhrase },
) {
    /** Actions other than [action] that already use [gesture]. */
    fun conflictsFor(action: GestureAction, gesture: FacialGesture): List<GestureAction> =
        gestureAssignments.filter { (other, g) -> other != action && g == gesture }.keys.toList()
}
