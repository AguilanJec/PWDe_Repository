package com.pwde.app.data.local

import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.prefs.InputMode

/** A saved calibration profile made from the working controls. Timestamps are set on save. */
fun ControlConfig.toCalibrationProfile(name: String, inputMode: InputMode, id: Long = 0): CalibrationProfile =
    CalibrationProfile(
        id = id,
        name = name,
        inputMode = inputMode.name,
        cursorSpeedUp = cursor.speedUp,
        cursorSpeedDown = cursor.speedDown,
        cursorSpeedLeft = cursor.speedLeft,
        cursorSpeedRight = cursor.speedRight,
        cursorSmoothing = cursor.smoothing,
        joystickSensitivity = joystick.sensitivity,
        joystickDeadZone = joystick.deadZone,
        joystickRadius = joystick.size,
        joystickCenterPitch = joystick.centerPitch,
        joystickCenterRoll = joystick.centerRoll,
        gestureAssignmentsJson = ControlJson.encodeGestures(gestureAssignments),
        gestureSensitivityJson = ControlJson.encodeSensitivity(gestureSensitivity),
        voiceEnabled = voiceEnabled,
        voiceMatchMode = voiceMatchMode.name,
        voiceActivationMode = voiceActivationMode.name,
        createdAt = 0,
        updatedAt = 0,
    )

/** The working controls a saved profile describes. Voice shortcuts aren't part of a profile. */
fun CalibrationProfile.toControlConfig(keepShortcutsFrom: ControlConfig): ControlConfig = keepShortcutsFrom.copy(
    gestureAssignments = ControlJson.decodeGestures(gestureAssignmentsJson),
    gestureSensitivity = ControlJson.decodeSensitivity(gestureSensitivityJson),
    voiceEnabled = voiceEnabled,
    voiceMatchMode = VoiceMatchMode.entries.firstOrNull { it.name == voiceMatchMode } ?: VoiceMatchMode.WORD_ANYWHERE,
    voiceActivationMode = VoiceActivationMode.entries.firstOrNull { it.name == voiceActivationMode } ?: VoiceActivationMode.IMMEDIATE,
    cursor = CursorTuning(cursorSpeedUp, cursorSpeedDown, cursorSpeedLeft, cursorSpeedRight, cursorSmoothing),
    joystick = JoystickTuning(joystickRadius, joystickSensitivity, joystickDeadZone, joystickCenterPitch, joystickCenterRoll),
)

val CalibrationProfile.inputModeOrDefault: InputMode
    get() = InputMode.entries.firstOrNull { it.name == inputMode } ?: InputMode.HEAD_FACE
