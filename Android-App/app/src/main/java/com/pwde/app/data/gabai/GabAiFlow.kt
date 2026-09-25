package com.pwde.app.data.gabai

import com.pwde.app.data.model.FaceOutputMode

/**
 * GabAI's transitions, as pure functions of (state, form) so every path is unit-tested.
 * The ViewModel does the side effects (saving profiles, persisting the session) around these.
 */
object GabAiFlow {
    fun newCalibration(): GabAiState = GabAiState.ChooseCalibrationMode

    /** A game already chosen (e.g. started from a game's page) skips straight to the calibration pick. */
    fun newGameProfile(form: GabAiForm): GabAiState =
        if (form.gameId != null) GabAiState.ConfirmCalibrationProfile else GabAiState.ChooseGame

    fun modeChosen(mode: FaceOutputMode): GabAiState = when (mode) {
        FaceOutputMode.CURSOR -> GabAiState.CalibrateCursorAxis(Axis.UP)
        FaceOutputMode.JOYSTICK -> GabAiState.CalibrateJoystick
    }

    fun axisDone(axis: Axis): GabAiState =
        axis.next()?.let { GabAiState.CalibrateCursorAxis(it) } ?: GabAiState.CalibrationVoiceSetup

    fun joystickDone(): GabAiState = GabAiState.CalibrationVoiceSetup

    fun calibrationSaved(): GabAiState = GabAiState.CalibrationSaved

    /** "Continue to a game profile" after saving a calibration. */
    fun continueToGame(form: GabAiForm): GabAiState =
        if (form.gameId != null) GabAiState.ConfirmCalibrationProfile else GabAiState.ChooseGame

    fun gameChosen(): GabAiState = GabAiState.ConfirmCalibrationProfile

    fun calibrationConfirmed(): GabAiState = GabAiState.UploadScreenshot

    fun screenshotDone(form: GabAiForm): GabAiState = GabAiState.ButtonMapping(form.buttons.size)

    /** Needs at least one button; otherwise stays on mapping. */
    fun buttonsDone(form: GabAiForm): GabAiState =
        if (form.buttons.isEmpty()) GabAiState.ButtonMapping(0) else GabAiState.TriggerAssignment(0, form.buttons.size)

    fun triggerDone(state: GabAiState.TriggerAssignment): GabAiState =
        if (state.buttonIndex + 1 < state.totalButtons) state.copy(buttonIndex = state.buttonIndex + 1)
        else GabAiState.NameAndSaveProfile

    fun profileSaved(): GabAiState = GabAiState.ProfileSaved

    fun createAnother(): GabAiState = GabAiState.ChooseGame

    /** One step back, or null to leave GabAI from the Welcome screen. */
    fun back(state: GabAiState, form: GabAiForm): GabAiState? = when (state) {
        GabAiState.Welcome -> null
        GabAiState.ChooseCalibrationMode ->
            if (form.continueToGame) GabAiState.ConfirmCalibrationProfile else GabAiState.Welcome
        is GabAiState.CalibrateCursorAxis ->
            state.axis.previous()?.let { GabAiState.CalibrateCursorAxis(it) } ?: GabAiState.ChooseCalibrationMode
        GabAiState.CalibrateJoystick -> GabAiState.ChooseCalibrationMode
        GabAiState.CalibrationVoiceSetup ->
            if (form.calibrationMode == FaceOutputMode.JOYSTICK) GabAiState.CalibrateJoystick
            else GabAiState.CalibrateCursorAxis(Axis.DIAGONAL)
        GabAiState.CalibrationSaved -> GabAiState.Welcome
        GabAiState.ChooseGame -> GabAiState.Welcome
        GabAiState.ConfirmCalibrationProfile -> GabAiState.ChooseGame
        GabAiState.UploadScreenshot -> GabAiState.ConfirmCalibrationProfile
        is GabAiState.ButtonMapping -> GabAiState.UploadScreenshot
        is GabAiState.TriggerAssignment ->
            if (state.buttonIndex == 0) GabAiState.ButtonMapping(form.buttons.size)
            else state.copy(buttonIndex = state.buttonIndex - 1)
        GabAiState.NameAndSaveProfile ->
            if (form.buttons.isEmpty()) GabAiState.ButtonMapping(0)
            else GabAiState.TriggerAssignment(form.buttons.size - 1, form.buttons.size)
        GabAiState.ProfileSaved -> GabAiState.Welcome
    }
}
