package com.pwde.app.data.model

/** How a mapped on-screen game button gets pressed. */
enum class TriggerType(val label: String) {
    VOICE("Voice command"),
    GESTURE("Head gesture"),
    JOYSTICK("Joystick action"),
}

/**
 * One trigger. [value] depends on [type]: the spoken phrase (VOICE), a [FacialGesture] name
 * (GESTURE), or a joystick direction name such as "UP_LEFT" (JOYSTICK).
 */
data class ButtonTrigger(val type: TriggerType, val value: String) {
    val gesture: FacialGesture? get() = if (type == TriggerType.GESTURE) FacialGesture.entries.firstOrNull { it.name == value } else null

    fun describe(): String = when (type) {
        TriggerType.VOICE -> "Say \"$value\""
        TriggerType.GESTURE -> gesture?.label ?: value
        TriggerType.JOYSTICK -> "Joystick ${value.lowercase().replace('_', '-')}"
    }
}

/**
 * A game button placed on the game's screenshot. Position is the button's center, normalized to
 * the screenshot (0–1 on both axes), so it survives any screen size.
 */
data class MappedButton(
    val id: Int,
    val label: String,
    val x: Float,
    val y: Float,
    val trigger: ButtonTrigger? = null,
)
