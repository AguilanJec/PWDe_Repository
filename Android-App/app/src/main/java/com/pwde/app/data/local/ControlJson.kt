package com.pwde.app.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
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

    fun encodeShortcuts(map: Map<VoiceShortcut, String>): String =
        gson.toJson(map.entries.associate { (shortcut, phrase) -> shortcut.name to phrase })

    fun decodeShortcuts(json: String?): Map<VoiceShortcut, String> {
        val stored = decodeStringMap(json)
        return VoiceShortcut.entries.associateWith { stored[it.name] ?: it.defaultPhrase }
    }

    private fun decodeStringMap(json: String?): Map<String, String> =
        if (json.isNullOrBlank()) emptyMap()
        else runCatching { gson.fromJson<Map<String, String>>(json, stringMapType) }.getOrNull().orEmpty()
}
