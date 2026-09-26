package com.pwde.app.gabai

import com.pwde.app.data.gabai.DetectedButton
import com.pwde.app.data.gabai.detectedToButtons
import com.pwde.app.data.model.ButtonTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectedButtonsTest {
    private fun detected(name: String, x: Float, y: Float) = DetectedButton(name, 0.9f, x, y)

    @Test
    fun theJoystickBecomesTheMovementJoystick() {
        val buttons = detectedToButtons(
            listOf(detected("skill_button", 0.8f, 0.7f), detected("joystick", 0.15f, 0.75f), detected("basic_attack", 0.9f, 0.85f)),
            firstId = 5,
        )
        val stick = buttons.single { it.label == "Movement joystick" }
        assertEquals(ButtonTrigger.MOVEMENT, stick.trigger)
        assertEquals(0.15f, stick.x, 0f)
        // Everything else still waits for the user to choose a trigger.
        assertEquals(listOf(null, null), buttons.filter { it !== stick }.map { it.trigger })
    }

    @Test
    fun buttonsAreOrderedTopToBottomWithFreshIdsAndNumberedRepeats() {
        val buttons = detectedToButtons(
            listOf(detected("skill_button", 0.8f, 0.9f), detected("skill_button", 0.7f, 0.6f), detected("recall", 0.5f, 0.2f)),
            firstId = 3,
        )
        assertEquals(listOf("Recall", "Skill button", "Skill button 2"), buttons.map { it.label })
        assertEquals(listOf(3, 4, 5), buttons.map { it.id })
    }

    @Test
    fun onlyTheFirstJoystickIsTheMovementJoystick() {
        val buttons = detectedToButtons(listOf(detected("joystick", 0.1f, 0.7f), detected("joystick", 0.2f, 0.8f)), firstId = 1)
        assertEquals(ButtonTrigger.MOVEMENT, buttons[0].trigger)
        assertNull(buttons[1].trigger)
        assertEquals("Joystick", buttons[1].label)
    }
}
