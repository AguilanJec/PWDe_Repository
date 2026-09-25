package com.pwde.app.data.model

import com.pwde.app.data.prefs.InputMode

/**
 * Static catalog of facial gestures PWDe recognises through face tracking. Two kinds:
 * - curated gestures ([isRaw] false): the GameFace set plus a few more, some combining several
 *   blendshapes, and head-pose moves (tilt, nod, shake);
 * - all 52 raw MediaPipe Face Landmarker blendshapes ([isRaw] true, [blendshape] set), each one
 *   usable as a gesture on its own. Sides are as MediaPipe names them.
 * Stored by name, so order doesn't matter.
 */
enum class FacialGesture(val label: String, val description: String, val blendshape: String? = null) {
    SMILE("Smile", "A wide smile"),
    FROWN("Frown", "Pull the corners of your mouth down"),
    OPEN_MOUTH("Open mouth", "Open your mouth, then close it"),
    EYEBROW_RAISE("Eyebrow raise", "Raise both eyebrows"),
    TILT_LEFT("Tilt left", "Tilt your head to the left"),
    TILT_RIGHT("Tilt right", "Tilt your head to the right"),
    NOD("Nod", "A small nod down and back up"),
    WINK("Wink", "Close one eye briefly"),

    // Mouth
    MOUTH_LEFT("Mouth left", "Push your lips to the left"),
    MOUTH_RIGHT("Mouth right", "Push your lips to the right"),
    PUCKER("Pucker", "Push your lips forward, like a kiss"),
    CHEEK_PUFF("Puff cheeks", "Fill your cheeks with air"),
    ROLL_LOWER_LIP("Roll lower lip", "Tuck your lower lip in"),
    JAW_LEFT("Jaw left", "Slide your jaw to the left"),
    JAW_RIGHT("Jaw right", "Slide your jaw to the right"),

    // Eyebrows
    RAISE_LEFT_EYEBROW("Raise left eyebrow", "Lift only your left eyebrow"),
    RAISE_RIGHT_EYEBROW("Raise right eyebrow", "Lift only your right eyebrow"),
    LOWER_LEFT_EYEBROW("Lower left eyebrow", "Pull your left eyebrow down"),
    LOWER_RIGHT_EYEBROW("Lower right eyebrow", "Pull your right eyebrow down"),

    // Eyes
    WINK_LEFT("Wink left", "Close only your left eye"),
    WINK_RIGHT("Wink right", "Close only your right eye"),
    CLOSE_EYES("Close both eyes", "Close both eyes and hold for a moment"),
    LOOK_UP("Look up", "Look up with your eyes only"),
    LOOK_DOWN("Look down", "Look down with your eyes only"),

    // Head
    SHAKE("Shake head", "A small shake, left and right and back"),

    // All 52 raw MediaPipe blendshapes, in the model's order.
    MP_NEUTRAL("_neutral", "Resting face — MediaPipe's baseline score", blendshape = "_neutral"),
    MP_BROW_DOWN_LEFT("browDownLeft", "Pull your left eyebrow down", blendshape = "browDownLeft"),
    MP_BROW_DOWN_RIGHT("browDownRight", "Pull your right eyebrow down", blendshape = "browDownRight"),
    MP_BROW_INNER_UP("browInnerUp", "Raise the inner ends of both eyebrows", blendshape = "browInnerUp"),
    MP_BROW_OUTER_UP_LEFT("browOuterUpLeft", "Raise the outer end of your left eyebrow", blendshape = "browOuterUpLeft"),
    MP_BROW_OUTER_UP_RIGHT("browOuterUpRight", "Raise the outer end of your right eyebrow", blendshape = "browOuterUpRight"),
    MP_CHEEK_PUFF("cheekPuff", "Fill your cheeks with air", blendshape = "cheekPuff"),
    MP_CHEEK_SQUINT_LEFT("cheekSquintLeft", "Raise your left cheek", blendshape = "cheekSquintLeft"),
    MP_CHEEK_SQUINT_RIGHT("cheekSquintRight", "Raise your right cheek", blendshape = "cheekSquintRight"),
    MP_EYE_BLINK_LEFT("eyeBlinkLeft", "Close your left eye", blendshape = "eyeBlinkLeft"),
    MP_EYE_BLINK_RIGHT("eyeBlinkRight", "Close your right eye", blendshape = "eyeBlinkRight"),
    MP_EYE_LOOK_DOWN_LEFT("eyeLookDownLeft", "Left eye looks down", blendshape = "eyeLookDownLeft"),
    MP_EYE_LOOK_DOWN_RIGHT("eyeLookDownRight", "Right eye looks down", blendshape = "eyeLookDownRight"),
    MP_EYE_LOOK_IN_LEFT("eyeLookInLeft", "Left eye looks toward your nose", blendshape = "eyeLookInLeft"),
    MP_EYE_LOOK_IN_RIGHT("eyeLookInRight", "Right eye looks toward your nose", blendshape = "eyeLookInRight"),
    MP_EYE_LOOK_OUT_LEFT("eyeLookOutLeft", "Left eye looks away from your nose", blendshape = "eyeLookOutLeft"),
    MP_EYE_LOOK_OUT_RIGHT("eyeLookOutRight", "Right eye looks away from your nose", blendshape = "eyeLookOutRight"),
    MP_EYE_LOOK_UP_LEFT("eyeLookUpLeft", "Left eye looks up", blendshape = "eyeLookUpLeft"),
    MP_EYE_LOOK_UP_RIGHT("eyeLookUpRight", "Right eye looks up", blendshape = "eyeLookUpRight"),
    MP_EYE_SQUINT_LEFT("eyeSquintLeft", "Squint your left eye", blendshape = "eyeSquintLeft"),
    MP_EYE_SQUINT_RIGHT("eyeSquintRight", "Squint your right eye", blendshape = "eyeSquintRight"),
    MP_EYE_WIDE_LEFT("eyeWideLeft", "Open your left eye wide", blendshape = "eyeWideLeft"),
    MP_EYE_WIDE_RIGHT("eyeWideRight", "Open your right eye wide", blendshape = "eyeWideRight"),
    MP_JAW_FORWARD("jawForward", "Push your jaw forward", blendshape = "jawForward"),
    MP_JAW_LEFT("jawLeft", "Slide your jaw to the left", blendshape = "jawLeft"),
    MP_JAW_OPEN("jawOpen", "Open your jaw", blendshape = "jawOpen"),
    MP_JAW_RIGHT("jawRight", "Slide your jaw to the right", blendshape = "jawRight"),
    MP_MOUTH_CLOSE("mouthClose", "Keep your lips together while your jaw opens", blendshape = "mouthClose"),
    MP_MOUTH_DIMPLE_LEFT("mouthDimpleLeft", "Pull the left corner of your mouth back", blendshape = "mouthDimpleLeft"),
    MP_MOUTH_DIMPLE_RIGHT("mouthDimpleRight", "Pull the right corner of your mouth back", blendshape = "mouthDimpleRight"),
    MP_MOUTH_FROWN_LEFT("mouthFrownLeft", "Pull the left corner of your mouth down", blendshape = "mouthFrownLeft"),
    MP_MOUTH_FROWN_RIGHT("mouthFrownRight", "Pull the right corner of your mouth down", blendshape = "mouthFrownRight"),
    MP_MOUTH_FUNNEL("mouthFunnel", "Round your lips into an O", blendshape = "mouthFunnel"),
    MP_MOUTH_LEFT("mouthLeft", "Push your lips to the left", blendshape = "mouthLeft"),
    MP_MOUTH_LOWER_DOWN_LEFT("mouthLowerDownLeft", "Pull the left side of your lower lip down", blendshape = "mouthLowerDownLeft"),
    MP_MOUTH_LOWER_DOWN_RIGHT("mouthLowerDownRight", "Pull the right side of your lower lip down", blendshape = "mouthLowerDownRight"),
    MP_MOUTH_PRESS_LEFT("mouthPressLeft", "Press the left side of your lips together", blendshape = "mouthPressLeft"),
    MP_MOUTH_PRESS_RIGHT("mouthPressRight", "Press the right side of your lips together", blendshape = "mouthPressRight"),
    MP_MOUTH_PUCKER("mouthPucker", "Push your lips forward, like a kiss", blendshape = "mouthPucker"),
    MP_MOUTH_RIGHT("mouthRight", "Push your lips to the right", blendshape = "mouthRight"),
    MP_MOUTH_ROLL_LOWER("mouthRollLower", "Tuck your lower lip in", blendshape = "mouthRollLower"),
    MP_MOUTH_ROLL_UPPER("mouthRollUpper", "Tuck your upper lip in", blendshape = "mouthRollUpper"),
    MP_MOUTH_SHRUG_LOWER("mouthShrugLower", "Push your lower lip up", blendshape = "mouthShrugLower"),
    MP_MOUTH_SHRUG_UPPER("mouthShrugUpper", "Push your upper lip up", blendshape = "mouthShrugUpper"),
    MP_MOUTH_SMILE_LEFT("mouthSmileLeft", "Smile with the left side of your mouth", blendshape = "mouthSmileLeft"),
    MP_MOUTH_SMILE_RIGHT("mouthSmileRight", "Smile with the right side of your mouth", blendshape = "mouthSmileRight"),
    MP_MOUTH_STRETCH_LEFT("mouthStretchLeft", "Stretch the left corner of your mouth sideways", blendshape = "mouthStretchLeft"),
    MP_MOUTH_STRETCH_RIGHT("mouthStretchRight", "Stretch the right corner of your mouth sideways", blendshape = "mouthStretchRight"),
    MP_MOUTH_UPPER_UP_LEFT("mouthUpperUpLeft", "Raise the left side of your upper lip", blendshape = "mouthUpperUpLeft"),
    MP_MOUTH_UPPER_UP_RIGHT("mouthUpperUpRight", "Raise the right side of your upper lip", blendshape = "mouthUpperUpRight"),
    MP_NOSE_SNEER_LEFT("noseSneerLeft", "Wrinkle the left side of your nose", blendshape = "noseSneerLeft"),
    MP_NOSE_SNEER_RIGHT("noseSneerRight", "Wrinkle the right side of your nose", blendshape = "noseSneerRight"),
    ;

    /** A single raw MediaPipe blendshape rather than a curated gesture. */
    val isRaw: Boolean get() = blendshape != null

    /** How to say it: the label, or a raw blendshape's name split into words ("brow down left"). */
    val spokenName: String
        get() = blendshape?.let { name ->
            if (name == "_neutral") "neutral" else name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ").lowercase()
        } ?: label.lowercase()

    companion object {
        val curated: List<FacialGesture> = entries.filter { !it.isRaw }
        val raw: List<FacialGesture> = entries.filter { it.isRaw }
    }
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

/** Levels are 1–10 everywhere, matching [com.pwde.app.ui.components.LevelStepper]. */
const val MIN_LEVEL = 1
const val MAX_LEVEL = 10
const val DEFAULT_LEVEL = 5

/** How the pointer follows head yaw/pitch. Speeds are per direction. */
data class CursorTuning(
    val speedUp: Int = DEFAULT_LEVEL,
    val speedDown: Int = DEFAULT_LEVEL,
    val speedLeft: Int = DEFAULT_LEVEL,
    val speedRight: Int = DEFAULT_LEVEL,
    val smoothing: Int = 7,
)

/** Head-tilt joystick. [centerPitch]/[centerRoll] are the user's neutral pose, in degrees. */
data class JoystickTuning(
    val size: Int = DEFAULT_LEVEL,
    val sensitivity: Int = DEFAULT_LEVEL,
    val deadZone: Int = 3,
    val centerPitch: Float = 0f,
    val centerRoll: Float = 0f,
)

/** What head movement drives: a free pointer or an 8-way joystick. */
enum class FaceOutputMode(val label: String) { CURSOR("Cursor"), JOYSTICK("Joystick") }

/** Joystick input drives the joystick; head & face and voice modes use the pointer. */
fun InputMode.faceOutputMode(): FaceOutputMode =
    if (this == InputMode.JOYSTICK) FaceOutputMode.JOYSTICK else FaceOutputMode.CURSOR

/** The user's working controls configuration (not yet saved as a named profile). */
data class ControlConfig(
    val gestureAssignments: Map<GestureAction, FacialGesture> = emptyMap(),
    val gestureSensitivity: Map<FacialGesture, Int> = emptyMap(),
    val voiceEnabled: Boolean = true,
    val voiceMatchMode: VoiceMatchMode = VoiceMatchMode.WORD_ANYWHERE,
    val voiceActivationMode: VoiceActivationMode = VoiceActivationMode.IMMEDIATE,
    val voiceShortcuts: Map<VoiceShortcut, String> = VoiceShortcut.entries.associateWith { it.defaultPhrase },
    val cursor: CursorTuning = CursorTuning(),
    val joystick: JoystickTuning = JoystickTuning(),
) {
    fun sensitivityOf(gesture: FacialGesture): Int = gestureSensitivity[gesture] ?: DEFAULT_LEVEL

    /** The action mapped to [gesture], if any. */
    fun actionFor(gesture: FacialGesture): GestureAction? =
        gestureAssignments.entries.firstOrNull { it.value == gesture }?.key

    /** Actions other than [action] that already use [gesture]. */
    fun conflictsFor(action: GestureAction, gesture: FacialGesture): List<GestureAction> =
        gestureAssignments.filter { (other, g) -> other != action && g == gesture }.keys.toList()
}
