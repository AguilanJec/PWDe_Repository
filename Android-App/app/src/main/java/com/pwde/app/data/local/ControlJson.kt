package com.pwde.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pwde.app.data.model.ButtonTrigger
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.MAX_LEVEL
import com.pwde.app.data.model.MIN_LEVEL
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.data.model.VoiceShortcut

/** Serialises control mappings to the JSON strings stored in Room. Unknown names are dropped. */
object ControlJson {
    private val gson = Gson()
    private val stringMapType = object : TypeToken<Map<String, String>>() {}.type

    fun encodeGestures(map: Map<GestureAction, FacialGesture>): String =
        gson.toJson(map.entries.associate { (action, gesture) -> action.name to gesture.name })

    fun decodeGestures(json: String?): Map<GestureAction, FacialGesture> =
        decodeStringMap(json).mapNotNull { (key, value) ->
            val action = GestureAction.entries.firstOrNull { it.name == key }
            val gesture = FacialGesture.entries.firstOrNull { it.name == value }
            if (action != null && gesture != null) action to gesture else null
        }.toMap()

    fun encodeSensitivity(map: Map<FacialGesture, Int>): String =
        gson.toJson(map.entries.associate { (gesture, level) -> gesture.name to level.toString() })

    /** Levels outside 1–10 are clamped; unparseable ones are dropped. */
    fun decodeSensitivity(json: String?): Map<FacialGesture, Int> =
        decodeStringMap(json).mapNotNull { (key, value) ->
            val gesture = FacialGesture.entries.firstOrNull { it.name == key }
            val level = value.toIntOrNull()?.coerceIn(MIN_LEVEL, MAX_LEVEL)
            if (gesture != null && level != null) gesture to level else null
        }.toMap()

    /** A set of gestures as a JSON list of names; null stays null ("never tested"). */
    fun encodeGestureSet(set: Set<FacialGesture>?): String? = set?.let { gestures -> gson.toJson(gestures.map { it.name }) }

    /** Unknown names are dropped; null or unreadable JSON decodes to null. */
    fun decodeGestureSet(json: String?): Set<FacialGesture>? {
        if (json.isNullOrBlank()) return null
        val names = runCatching { gson.fromJson<List<String>>(json, stringListType) }.getOrNull() ?: return null
        return names.mapNotNull { name -> FacialGesture.entries.firstOrNull { it.name == name } }.toSet()
    }

    private val stringListType = object : TypeToken<List<String>>() {}.type

    fun encodeShortcuts(map: Map<VoiceShortcut, String>): String =
        gson.toJson(map.entries.associate { (shortcut, phrase) -> shortcut.name to phrase })

    fun decodeShortcuts(json: String?): Map<VoiceShortcut, String> {
        val stored = decodeStringMap(json)
        return VoiceShortcut.entries.associateWith { stored[it.name] ?: it.defaultPhrase }
    }

    fun encodeButtons(buttons: List<MappedButton>): String = gson.toJson(buttons.map(ButtonJson::from))

    /** Malformed JSON or unknown trigger types decode to an empty list / no trigger, never a crash. */
    fun decodeButtons(json: String?): List<MappedButton> {
        if (json.isNullOrBlank()) return emptyList()
        val raw = runCatching { gson.fromJson<List<ButtonJson>>(json, buttonListType) }.getOrNull().orEmpty()
        return raw.mapNotNull { it.toModel() }
    }

    /** Gson-friendly shape (all nullable) so partial or older JSON still loads. */
    private data class ButtonJson(
        val id: Int? = null,
        val label: String? = null,
        val x: Float? = null,
        val y: Float? = null,
        val triggerType: String? = null,
        val triggerValue: String? = null,
    ) {
        fun toModel(): MappedButton? {
            val trigger = TriggerType.entries.firstOrNull { it.name == triggerType }
                ?.let { type -> triggerValue?.let { ButtonTrigger(type, it) } }
            return MappedButton(
                id = id ?: return null,
                label = label.orEmpty(),
                x = (x ?: 0.5f).coerceIn(0f, 1f),
                y = (y ?: 0.5f).coerceIn(0f, 1f),
                trigger = trigger,
            )
        }

        companion object {
            fun from(b: MappedButton) = ButtonJson(b.id, b.label, b.x, b.y, b.trigger?.type?.name, b.trigger?.value)
        }
    }

    private val buttonListType = object : TypeToken<List<ButtonJson>>() {}.type

    private fun decodeStringMap(json: String?): Map<String, String> =
        if (json.isNullOrBlank()) emptyMap()
        else runCatching { gson.fromJson<Map<String, String>>(json, stringMapType) }.getOrNull().orEmpty()
}
