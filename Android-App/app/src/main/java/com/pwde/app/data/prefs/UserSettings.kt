package com.pwde.app.data.prefs

/** Local-only user settings. Never synced to the cloud. */
data class UserSettings(
    val accessibilityNeeds: Set<AccessibilityNeed> = emptySet(),
    val colorScheme: ColorSchemeOption = ColorSchemeOption.DEFAULT,
    val textSize: TextSizeOption = TextSizeOption.MEDIUM,
    val layoutMode: LayoutMode = LayoutMode.STANDARD,
    val inputMode: InputMode = InputMode.HEAD_FACE,
    val ttsEnabled: Boolean = false,
    val ttsSpeed: TtsSpeed = TtsSpeed.NORMAL,
    val usesOtherScreenReader: Boolean = false,
    val setupCompleted: Boolean = false,
    val voiceTutorialCompleted: Boolean = false,
)

enum class AccessibilityNeed(val label: String, val description: String) {
    MOVEMENT("Movement & mobility", "Head, face, eye or switch controls"),
    SEEING("Seeing & reading", "Bigger text, contrast, read-aloud"),
    HEARING("Hearing & sound", "Captions and visual alerts"),
    SPEAKING("Speaking & communication", "Type or use gestures instead of voice"),
    OTHER("Other", "Something else — you can tune everything later"),
}

enum class ColorSchemeOption(val label: String) {
    DEFAULT("Default"),
    CONTRAST("High contrast"),
    LIGHT("Light"),
    COLOR_SAFE("Color-safe"),
}

enum class TextSizeOption(val label: String, val scale: Float) {
    SMALL("Small", 0.9f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.3f),
    X_LARGE("X-Large", 1.6f);

    val percentLabel: String get() = "${(scale * 100).toInt()}%"
}

enum class LayoutMode(val label: String, val description: String) {
    STANDARD("Standard", "Default gaps between controls"),
    COMPACT("Compact", "Tighter spacing — more fits on screen"),
    EASY_REACH("Easy reach", "Controls move to the lower half of the screen"),
}

enum class InputMode(val label: String, val description: String) {
    HEAD_FACE("Head & face", "Tilt your head; use face gestures to press"),
    JOYSTICK("Joystick", "On-screen joystick you steer with your head or tilt"),
    VOICE("Voice", "Say a button's name to press it"),
}

enum class TtsSpeed(val label: String, val rate: Float) {
    SLOW("Slow", 0.75f),
    NORMAL("Normal", 1.0f),
    FAST("Fast", 1.4f),
}
