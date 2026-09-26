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

    /** After voice, test every gesture the user hasn't already performed. */
    fun voiceDone(form: GabAiForm): GabAiState = nextGestureTest(form, after = -1)

    /** Performed or skipped: on to the next gesture not yet performed, or the results once none are left. */
    fun gestureTested(state: GabAiState.CalibrationGestureTest, form: GabAiForm): GabAiState =
        nextGestureTest(form, after = state.index)

    /** "Skip the rest" goes straight to the results. */
    fun gestureTestEnded(): GabAiState = GabAiState.CalibrationGestureReview

    /** From the results: another go at just the gestures that were missed. */
    fun retryMissedGestures(form: GabAiForm): GabAiState = nextGestureTest(form, after = -1)

    private fun nextGestureTest(form: GabAiForm, after: Int): GabAiState {
        val tests = GabAiState.GESTURE_TEST
        val next = ((after + 1) until tests.size).firstOrNull { tests[it] !in form.passedGestures }
        return next?.let { GabAiState.CalibrationGestureTest(it) } ?: GabAiState.CalibrationGestureReview
    }

    fun calibrationSaved(): GabAiState = GabAiState.CalibrationSaved

    /** "Continue to a game profile" after saving a calibration. */
    fun continueToGame(form: GabAiForm): GabAiState =
        if (form.gameId != null) GabAiState.ConfirmCalibrationProfile else GabAiState.ChooseGame

    fun gameChosen(): GabAiState = GabAiState.ConfirmCalibrationProfile

    fun calibrationConfirmed(): GabAiState = GabAiState.UploadScreenshot

    fun screenshotDone(form: GabAiForm): GabAiState = GabAiState.ButtonMapping(form.buttons.size)

    /** Needs at least one button; otherwise stays on mapping. */
    fun buttonsDone(form: GabAiForm): GabAiState =
        if (form.buttons.isEmpty()) GabAiState.ButtonMapping(0) else GabAiState.AssignTriggers

    /** Only once every button has a trigger: straight into testing them. */
    fun triggersDone(form: GabAiForm): GabAiState =
        if (form.buttons.isNotEmpty() && form.buttons.all { it.trigger != null }) GabAiState.TestControls
        else GabAiState.AssignTriggers

    fun testingDone(): GabAiState = GabAiState.NameAndSaveProfile

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
        is GabAiState.CalibrationGestureTest ->
            if (state.index == 0) GabAiState.CalibrationVoiceSetup else GabAiState.CalibrationGestureTest(state.index - 1)
        GabAiState.CalibrationGestureReview -> GabAiState.CalibrationGestureTest(GabAiState.GESTURE_TEST.lastIndex)
        GabAiState.CalibrationSaved -> GabAiState.Welcome
        GabAiState.ChooseGame -> GabAiState.Welcome
        GabAiState.ConfirmCalibrationProfile -> GabAiState.ChooseGame
        GabAiState.UploadScreenshot -> GabAiState.ConfirmCalibrationProfile
        is GabAiState.ButtonMapping -> GabAiState.UploadScreenshot
        GabAiState.AssignTriggers -> GabAiState.ButtonMapping(form.buttons.size)
        GabAiState.TestControls -> GabAiState.AssignTriggers
        GabAiState.NameAndSaveProfile ->
            if (form.buttons.isEmpty()) GabAiState.ButtonMapping(0) else GabAiState.TestControls
        GabAiState.ProfileSaved -> GabAiState.Welcome
    }
}
