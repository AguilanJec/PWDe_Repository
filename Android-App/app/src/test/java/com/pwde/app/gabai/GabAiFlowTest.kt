package com.pwde.app.gabai

import com.pwde.app.data.gabai.Axis
import com.pwde.app.data.gabai.GabAiCodec
import com.pwde.app.data.gabai.GabAiFlow
import com.pwde.app.data.gabai.GabAiForm
import com.pwde.app.data.gabai.GabAiState
import com.pwde.app.data.model.ButtonTrigger
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FaceOutputMode
import com.pwde.app.data.model.JoystickTuning
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GabAiFlowTest {
    private val threeButtons = GabAiForm(
        gameId = "mobile_legends",
        buttons = listOf(MappedButton(1, "Attack", 0.8f, 0.8f), MappedButton(2, "Skill", 0.7f, 0.7f), MappedButton(3, "Recall", 0.1f, 0.9f)),
    )

    @Test
    fun cursorCalibrationVisitsEveryAxisThenVoiceThenSaved() {
        val visited = mutableListOf<GabAiState>()
        var state: GabAiState = GabAiFlow.modeChosen(FaceOutputMode.CURSOR)
        while (state is GabAiState.CalibrateCursorAxis) {
            visited += state
            state = GabAiFlow.axisDone(state.axis)
        }
        assertEquals(Axis.entries.map { GabAiState.CalibrateCursorAxis(it) }, visited)
        assertEquals(GabAiState.CalibrationVoiceSetup, state)
        assertEquals(GabAiState.CalibrationSaved, GabAiFlow.calibrationSaved())
    }

    @Test
    fun joystickCalibrationGoesStraightToVoice() {
        assertEquals(GabAiState.CalibrateJoystick, GabAiFlow.modeChosen(FaceOutputMode.JOYSTICK))
        assertEquals(GabAiState.CalibrationVoiceSetup, GabAiFlow.joystickDone())
    }

    @Test
    fun afterCalibrationContinueToGameSkipsGamePickIfAlreadyChosen() {
        assertEquals(GabAiState.ChooseGame, GabAiFlow.continueToGame(GabAiForm()))
        assertEquals(GabAiState.ConfirmCalibrationProfile, GabAiFlow.continueToGame(GabAiForm(gameId = "clash_royale")))
    }

    @Test
    fun gameProfileBranchLoopsTriggersOncePerButton() {
        assertEquals(GabAiState.ChooseGame, GabAiFlow.newGameProfile(GabAiForm()))
        assertEquals(GabAiState.ConfirmCalibrationProfile, GabAiFlow.gameChosen())
        assertEquals(GabAiState.UploadScreenshot, GabAiFlow.calibrationConfirmed())
        assertEquals(GabAiState.ButtonMapping(3), GabAiFlow.screenshotDone(threeButtons))

        var state = GabAiFlow.buttonsDone(threeButtons)
        val seen = mutableListOf<GabAiState>()
        while (state is GabAiState.TriggerAssignment) {
            seen += state
            state = GabAiFlow.triggerDone(state)
        }
        assertEquals((0..2).map { GabAiState.TriggerAssignment(it, 3) }, seen)
        assertEquals(GabAiState.NameAndSaveProfile, state)
        assertEquals(GabAiState.ProfileSaved, GabAiFlow.profileSaved())
        assertEquals(GabAiState.ChooseGame, GabAiFlow.createAnother())
    }

    @Test
    fun cannotLeaveButtonMappingWithoutButtons() {
        assertEquals(GabAiState.ButtonMapping(0), GabAiFlow.buttonsDone(GabAiForm()))
    }

    @Test
    fun newGameProfileForAKnownGameSkipsTheGamePick() {
        assertEquals(GabAiState.ConfirmCalibrationProfile, GabAiFlow.newGameProfile(GabAiForm(gameId = "clash_royale")))
    }

    @Test
    fun backWalksEachBranchInReverse() {
        val cursor = GabAiForm(calibrationMode = FaceOutputMode.CURSOR)
        val joystick = GabAiForm(calibrationMode = FaceOutputMode.JOYSTICK)
        assertNull(GabAiFlow.back(GabAiState.Welcome, cursor))
        assertEquals(GabAiState.Welcome, GabAiFlow.back(GabAiState.ChooseCalibrationMode, cursor))
        assertEquals(GabAiState.ChooseCalibrationMode, GabAiFlow.back(GabAiState.CalibrateCursorAxis(Axis.UP), cursor))
        assertEquals(GabAiState.CalibrateCursorAxis(Axis.LEFT), GabAiFlow.back(GabAiState.CalibrateCursorAxis(Axis.RIGHT), cursor))
        assertEquals(GabAiState.CalibrateCursorAxis(Axis.DIAGONAL), GabAiFlow.back(GabAiState.CalibrationVoiceSetup, cursor))
        assertEquals(GabAiState.CalibrateJoystick, GabAiFlow.back(GabAiState.CalibrationVoiceSetup, joystick))
        assertEquals(GabAiState.ChooseGame, GabAiFlow.back(GabAiState.ConfirmCalibrationProfile, cursor))
        assertEquals(GabAiState.ButtonMapping(3), GabAiFlow.back(GabAiState.TriggerAssignment(0, 3), threeButtons))
        assertEquals(GabAiState.TriggerAssignment(1, 3), GabAiFlow.back(GabAiState.TriggerAssignment(2, 3), threeButtons))
        assertEquals(GabAiState.TriggerAssignment(2, 3), GabAiFlow.back(GabAiState.NameAndSaveProfile, threeButtons))
    }

    @Test
    fun calibrationStartedForAGameBacksOutToThatGame() {
        val form = GabAiForm(continueToGame = true, gameId = "clash_royale")
        assertEquals(GabAiState.ConfirmCalibrationProfile, GabAiFlow.back(GabAiState.ChooseCalibrationMode, form))
    }
}

class GabAiCodecTest {
    private val allStates: List<GabAiState> = listOf(
        GabAiState.Welcome, GabAiState.ChooseCalibrationMode, GabAiState.CalibrateJoystick,
        GabAiState.CalibrationVoiceSetup, GabAiState.CalibrationSaved, GabAiState.ChooseGame,
        GabAiState.ConfirmCalibrationProfile, GabAiState.UploadScreenshot, GabAiState.NameAndSaveProfile,
        GabAiState.ProfileSaved, GabAiState.ButtonMapping(4), GabAiState.TriggerAssignment(2, 5),
    ) + Axis.entries.map { GabAiState.CalibrateCursorAxis(it) }

    @Test
    fun everyStateRoundTrips() {
        allStates.forEach { assertEquals(it, GabAiCodec.decodeState(GabAiCodec.encodeState(it))) }
    }

    @Test
    fun unreadableStatesFallBackSafely() {
        assertEquals(GabAiState.Welcome, GabAiCodec.decodeState(null))
        assertEquals(GabAiState.Welcome, GabAiCodec.decodeState("SomethingNew"))
        assertEquals(GabAiState.ChooseCalibrationMode, GabAiCodec.decodeState("CalibrateCursorAxis:SIDEWAYS"))
        // An out-of-range button index can't be resumed as-is; go back to mapping.
        assertEquals(GabAiState.ButtonMapping(0), GabAiCodec.decodeState("TriggerAssignment:7:3"))
    }

    @Test
    fun formRoundTripsIncludingButtonsAndTriggers() {
        val form = GabAiForm(
            calibrationMode = FaceOutputMode.JOYSTICK,
            cursor = CursorTuning(2, 3, 4, 5, 6),
            joystick = JoystickTuning(size = 8, sensitivity = 2, deadZone = 4, centerPitch = -6.5f, centerRoll = 3f),
            voiceEnabled = false,
            matchMode = VoiceMatchMode.EXACT,
            activationMode = VoiceActivationMode.AFTER_FINISH,
            calibrationName = "Evening",
            savedCalibrationId = 7,
            continueToGame = true,
            gameId = "mobile_legends",
            calibrationProfileId = 7,
            screenshotPath = "/data/shot.img",
            buttons = listOf(
                MappedButton(1, "Attack", 0.8f, 0.75f, ButtonTrigger(TriggerType.VOICE, "attack")),
                MappedButton(2, "Skill 1", 0.6f, 0.8f, ButtonTrigger(TriggerType.GESTURE, "SMILE")),
                MappedButton(3, "Move", 0.2f, 0.7f, ButtonTrigger(TriggerType.JOYSTICK, "UP_LEFT")),
                MappedButton(4, "Unassigned", 0.5f, 0.5f),
            ),
            profileName = "Ranked",
            editingGameProfileId = 3,
            savedGameProfileId = 3,
        )
        assertEquals(form, GabAiCodec.decodeForm(GabAiCodec.encodeForm(form)))
    }

    @Test
    fun missingOrBrokenFormJsonGivesDefaults() {
        assertEquals(GabAiForm(), GabAiCodec.decodeForm(null))
        assertEquals(GabAiForm(), GabAiCodec.decodeForm("not json"))
        assertEquals(GabAiForm(gameId = "clash_royale"), GabAiCodec.decodeForm("""{"gameId":"clash_royale"}"""))
    }
}
