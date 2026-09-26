package com.pwde.app.data.local

import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.MAX_LEVEL
import com.pwde.app.data.model.MIN_LEVEL
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.model.VoiceShortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Persists the working controls configuration (gestures, voice, cursor, joystick) to Room. */
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

    suspend fun setGestureSensitivity(gesture: FacialGesture, level: Int) = edit {
        it.copy(gestureSensitivity = it.gestureSensitivity + (gesture to level.coerceIn(MIN_LEVEL, MAX_LEVEL)))
    }

    suspend fun setCursorTuning(tuning: CursorTuning) = edit { it.copy(cursor = tuning.clamped()) }

    /** Size, sensitivity and dead zone. The center is kept; set it with [setJoystickCenter]. */
    suspend fun setJoystickTuning(tuning: JoystickTuning) = edit {
        it.copy(joystick = tuning.clamped().copy(centerPitch = it.joystick.centerPitch, centerRoll = it.joystick.centerRoll))
    }

    suspend fun setJoystickCenter(pitch: Float, roll: Float) = edit {
        it.copy(joystick = it.joystick.copy(centerPitch = pitch, centerRoll = roll))
    }

    /** Makes a saved calibration profile the working controls (voice shortcuts are kept). */
    suspend fun applyCalibration(profile: CalibrationProfile) = edit { profile.toControlConfig(keepShortcutsFrom = it) }

    /** Replaces the working controls wholesale, e.g. with GabAI's in-progress calibration. */
    suspend fun replace(config: ControlConfig) = edit { config }

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

private fun Int.level() = coerceIn(MIN_LEVEL, MAX_LEVEL)

private fun CursorTuning.clamped() = CursorTuning(speedUp.level(), speedDown.level(), speedLeft.level(), speedRight.level(), smoothing.level())

private fun JoystickTuning.clamped() = copy(size = size.level(), sensitivity = sensitivity.level(), deadZone = deadZone.level())

private fun ControlSettingsEntity.toConfig() = ControlConfig(
    gestureAssignments = ControlJson.decodeGestures(gestureAssignmentsJson),
    gestureSensitivity = ControlJson.decodeSensitivity(gestureSensitivityJson),
    voiceEnabled = voiceEnabled,
    voiceMatchMode = VoiceMatchMode.entries.firstOrNull { it.name == voiceMatchMode } ?: VoiceMatchMode.WORD_ANYWHERE,
    voiceActivationMode = VoiceActivationMode.entries.firstOrNull { it.name == voiceActivationMode }
        ?: VoiceActivationMode.IMMEDIATE,
    voiceShortcuts = ControlJson.decodeShortcuts(voiceShortcutsJson),
    cursor = CursorTuning(cursorSpeedUp, cursorSpeedDown, cursorSpeedLeft, cursorSpeedRight, cursorSmoothing).clamped(),
    joystick = JoystickTuning(joystickSize, joystickSensitivity, joystickDeadZone, joystickCenterPitch, joystickCenterRoll).clamped(),
    enabledGestures = ControlJson.decodeGestureSet(enabledGesturesJson),
)

private fun ControlConfig.toEntity(now: Long) = ControlSettingsEntity(
    gestureAssignmentsJson = ControlJson.encodeGestures(gestureAssignments),
    voiceEnabled = voiceEnabled,
    voiceMatchMode = voiceMatchMode.name,
    voiceActivationMode = voiceActivationMode.name,
    voiceShortcutsJson = ControlJson.encodeShortcuts(voiceShortcuts),
    updatedAt = now,
    gestureSensitivityJson = ControlJson.encodeSensitivity(gestureSensitivity),
    cursorSpeedUp = cursor.speedUp,
    cursorSpeedDown = cursor.speedDown,
    cursorSpeedLeft = cursor.speedLeft,
    cursorSpeedRight = cursor.speedRight,
    cursorSmoothing = cursor.smoothing,
    joystickSize = joystick.size,
    joystickSensitivity = joystick.sensitivity,
    joystickDeadZone = joystick.deadZone,
    joystickCenterPitch = joystick.centerPitch,
    joystickCenterRoll = joystick.centerRoll,
    enabledGesturesJson = ControlJson.encodeGestureSet(enabledGestures),
)
