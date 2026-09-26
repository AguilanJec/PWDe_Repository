package com.pwde.app.play

import com.pwde.app.data.model.ButtonTrigger
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.MappedButton
import com.pwde.app.data.model.TriggerType
import com.pwde.app.sensors.face.GazeDwell
import com.pwde.app.sensors.face.JoystickDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameInputTest {
    private val gesture = FacialGesture.entries.first()
    private val skill = MappedButton(1, "Skill", 0.8f, 0.7f, ButtonTrigger(TriggerType.VOICE, "fire"))
    private val attack = MappedButton(2, "Attack", 0.9f, 0.8f, ButtonTrigger(TriggerType.GESTURE, gesture.name))
    private val recall = MappedButton(3, "Recall", 0.5f, 0.9f, ButtonTrigger(TriggerType.JOYSTICK, JoystickDirection.UP.name))
    private val buttons = listOf(skill, attack, recall)

    @Test
    fun voiceTriggersBecomeBindings() {
        val bindings = GameInput.bindings(buttons)
        assertTrue(bindings.containsAll(GameInput.STANDARD_BINDINGS))
        assertEquals(listOf("fire"), bindings.single { it.commandId == GameInput.buttonCommandId(1) }.phrases)
        assertNull(bindings.firstOrNull { it.commandId == GameInput.buttonCommandId(2) })
    }

    @Test
    fun voiceCommandsMapToGameCommands() {
        assertEquals(GameCommand.Press(skill), GameInput.fromVoice(GameInput.buttonCommandId(1), "fire", buttons))
        assertEquals(GameCommand.Exit, GameInput.fromVoice(GameInput.MENU, "pwde menu", buttons))
        assertEquals(GameCommand.Back, GameInput.fromVoice(GameInput.BACK, "back", buttons))
        assertEquals(GameCommand.HideOverlay, GameInput.fromVoice(GameInput.HIDE_OVERLAY, "hide overlay", buttons))
        assertEquals(GameCommand.ShowControls, GameInput.fromVoice(GameInput.SHOW_CONTROLS, "show controls", buttons))
        assertEquals(GameCommand.HideControls, GameInput.fromVoice(GameInput.HIDE_CONTROLS, "hide controls", buttons))
        assertTrue(GameInput.worksWhilePaused(GameCommand.ShowControls))
        assertTrue(GameInput.fromVoice(null, "banana", buttons) is GameCommand.Ignored)
        assertNull(GameInput.fromVoice(null, null, buttons))
    }

    /**
     * The in-game spotter hears the game's own audio, so ending the session can't be a bare, common
     * word: the game saying "menu" used to stop the session and pull PWDe over the game.
     */
    @Test
    fun leavingTheGameNeedsAnExplicitPhrase() {
        val phrases = GameInput.STANDARD_BINDINGS.flatMap { it.phrases }
        assertFalse("menu" in phrases)
        assertFalse("main menu" in phrases)
        assertFalse("exit" in phrases)
        assertFalse("quit" in phrases)
        assertEquals(GameCommand.Exit, GameInput.fromVoice(GameInput.EXIT, "exit game", buttons))
        assertEquals(GameCommand.Exit, GameInput.fromVoice(GameInput.EXIT, "stop pwde", buttons))
    }

    @Test
    fun cursorModeVoiceCommands() {
        val scrollDown = GameInput.STANDARD_BINDINGS.single { "scroll down" in it.phrases }
        assertEquals(GameCommand.Scroll(ScrollDirection.DOWN), GameInput.fromVoice(scrollDown.commandId, "scroll down", buttons))
        assertEquals(GameCommand.StartDrag, GameInput.fromVoice(GameInput.DRAG, "drag", buttons))
        assertEquals(GameCommand.Recents, GameInput.fromVoice(GameInput.RECENTS, "recent apps", buttons))
        assertEquals(GameCommand.JoystickMode, GameInput.fromVoice(GameInput.JOYSTICK_MODE, "joystick mode", buttons))
        // "drop" and mode switches still work while paused, so a drag can always be let go.
        assertTrue(GameInput.worksWhilePaused(GameCommand.Drop))
        assertFalse(GameInput.worksWhilePaused(GameCommand.StartDrag))
    }

    @Test
    fun aButtonMappedToAGestureWinsOverItsAction() {
        assertEquals(GameCommand.Press(attack), GameInput.fromGesture(gesture, buttons, ControlConfig()))
    }

    @Test
    fun joystickDirectionsPressTheirButton() {
        assertEquals(GameCommand.Press(recall), GameInput.fromJoystick(JoystickDirection.UP, buttons))
        assertNull(GameInput.fromJoystick(JoystickDirection.DOWN, buttons))
        assertNull(GameInput.fromJoystick(JoystickDirection.CENTER, buttons))
    }

    @Test
    fun onlyPwdeControlsWorkWhilePaused() {
        assertTrue(GameInput.worksWhilePaused(GameCommand.Resume))
        assertTrue(GameInput.worksWhilePaused(GameCommand.Exit))
        assertFalse(GameInput.worksWhilePaused(GameCommand.Press(skill)))
        assertFalse(GameInput.worksWhilePaused(GameCommand.Select))
        assertFalse(GameInput.worksWhilePaused(GameCommand.Back))
    }

    // ---- eye control: a held gaze presses the button it settles on --------------------------------------

    @Test
    fun aGazeOnAButtonPressesIt() {
        assertEquals(GameCommand.Press(attack), GameInput.fromGaze(GazeDwell(0.9f, 0.8f), buttons))
    }

    @Test
    fun aGazeNearAButtonStillCountsIt() {
        // Just inside the tolerance of "Attack" at (0.9, 0.8).
        assertEquals(GameCommand.Press(attack), GameInput.fromGaze(GazeDwell(0.85f, 0.75f), buttons))
    }

    @Test
    fun aGazeBetweenTwoButtonsTakesTheNearerOne() {
        // (0.82, 0.8) is nearer Attack (0.9, 0.8) than Skill (0.8, 0.7).
        assertEquals(GameCommand.Press(attack), GameInput.fromGaze(GazeDwell(0.82f, 0.8f), buttons))
    }

    @Test
    fun aGazeOnNothingIsExplainedRatherThanIgnored() {
        // Holding still on empty screen and having nothing happen is indistinguishable from eye
        // control being broken, so it has to say so.
        val command = GameInput.fromGaze(GazeDwell(0.5f, 0.5f), buttons)
        assertTrue(command is GameCommand.Ignored)
    }

    @Test
    fun aProfileWithNoButtonsSaysSo() {
        val command = GameInput.fromGaze(GazeDwell(0.5f, 0.5f), emptyList())
        assertTrue(command is GameCommand.Ignored)
        assertEquals("This game has no mapped buttons yet", (command as GameCommand.Ignored).reason)
    }

    @Test
    fun aButtonWithNoTriggerIsNotAGazeTarget() {
        val unbound = MappedButton(9, "Unbound", 0.5f, 0.5f, trigger = null)
        assertNull(GameInput.buttonAt(listOf(unbound), 0.5f, 0.5f))
    }

    @Test
    fun theMovementStickIsSteeredNotTapped() {
        val stick = MappedButton(4, "Move", 0.2f, 0.8f, ButtonTrigger.MOVEMENT)
        assertNull(GameInput.buttonAt(listOf(stick), 0.2f, 0.8f))
    }

    @Test
    fun eyeModeCanBeSpokenAndWorksWhilePaused() {
        assertEquals(GameCommand.EyeMode, GameInput.fromVoice(GameInput.EYE_MODE, "eye mode", buttons))
        assertTrue(GameInput.worksWhilePaused(GameCommand.EyeMode))
    }
}
