package com.pwde.app.data.local

import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.model.VoiceShortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persists the working controls configuration (gesture picks, voice options) to Room. */
class ControlsRepository(
    private val dao: ControlSettingsDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val writeLock = Mutex()

    val config: Flow<ControlConfig> = dao.observe().map { it?.toConfig() ?: ControlConfig() }

    suspend fun setGesture(action: GestureAction, gesture: FacialGesture?) = edit { config ->
        val updated = config.gestureAssignments.toMutableMap()
        if (gesture == null) updated.remove(action) else updated[action] = gesture
        config.copy(gestureAssignments = updated)
    }

    suspend fun setVoiceEnabled(enabled: Boolean) = edit { it.copy(voiceEnabled = enabled) }

    suspend fun setVoiceMatchMode(mode: VoiceMatchMode) = edit { it.copy(voiceMatchMode = mode) }

    suspend fun setVoiceActivationMode(mode: VoiceActivationMode) = edit { it.copy(voiceActivationMode = mode) }

    suspend fun setVoiceShortcut(shortcut: VoiceShortcut, phrase: String) = edit {
        it.copy(voiceShortcuts = it.voiceShortcuts + (shortcut to phrase))
    }

    private suspend fun edit(transform: (ControlConfig) -> ControlConfig) = writeLock.withLock {
        val current = dao.get()?.toConfig() ?: ControlConfig()
        dao.upsert(transform(current).toEntity(clock()))
    }
}

private fun ControlSettingsEntity.toConfig() = ControlConfig(
    gestureAssignments = ControlJson.decodeGestures(gestureAssignmentsJson),
    voiceEnabled = voiceEnabled,
    voiceMatchMode = VoiceMatchMode.entries.firstOrNull { it.name == voiceMatchMode } ?: VoiceMatchMode.WORD_ANYWHERE,
    voiceActivationMode = VoiceActivationMode.entries.firstOrNull { it.name == voiceActivationMode }
        ?: VoiceActivationMode.IMMEDIATE,
    voiceShortcuts = ControlJson.decodeShortcuts(voiceShortcutsJson),
)

private fun ControlConfig.toEntity(now: Long) = ControlSettingsEntity(
    gestureAssignmentsJson = ControlJson.encodeGestures(gestureAssignments),
    voiceEnabled = voiceEnabled,
    voiceMatchMode = voiceMatchMode.name,
    voiceActivationMode = voiceActivationMode.name,
    voiceShortcutsJson = ControlJson.encodeShortcuts(voiceShortcuts),
    updatedAt = now,
)
