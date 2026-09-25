package com.pwde.app.data

import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.VoiceShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlJsonTest {
    @Test
    fun gestures_roundTrip() {
        val map = mapOf(GestureAction.SELECT to FacialGesture.OPEN_MOUTH, GestureAction.BACK to FacialGesture.TILT_LEFT)
        assertEquals(map, ControlJson.decodeGestures(ControlJson.encodeGestures(map)))
    }

    @Test
    fun gestures_unknownOrBrokenJsonIsDropped() {
        assertEquals(
            mapOf(GestureAction.HOME to FacialGesture.SMILE),
            ControlJson.decodeGestures("""{"HOME":"SMILE","NOT_AN_ACTION":"SMILE","BACK":"NOT_A_GESTURE"}"""),
        )
        assertTrue(ControlJson.decodeGestures("not json").isEmpty())
        assertTrue(ControlJson.decodeGestures(null).isEmpty())
    }

    @Test
    fun shortcuts_missingEntriesFallBackToDefaults() {
        val decoded = ControlJson.decodeShortcuts("""{"CURSOR_MODE":"pointer please"}""")
        assertEquals("pointer please", decoded[VoiceShortcut.CURSOR_MODE])
        assertEquals(VoiceShortcut.SWITCH_PROFILE.defaultPhrase, decoded[VoiceShortcut.SWITCH_PROFILE])
    }

    @Test
    fun conflictsFor_listsOtherActionsUsingTheSameGesture() {
        val config = ControlConfig(
            gestureAssignments = mapOf(
                GestureAction.RECENTER to FacialGesture.WINK,
                GestureAction.TOUCH_HOLD to FacialGesture.WINK,
                GestureAction.SELECT to FacialGesture.SMILE,
            ),
        )
        assertEquals(listOf(GestureAction.TOUCH_HOLD), config.conflictsFor(GestureAction.RECENTER, FacialGesture.WINK))
        assertTrue(config.conflictsFor(GestureAction.SELECT, FacialGesture.SMILE).isEmpty())
    }
}
