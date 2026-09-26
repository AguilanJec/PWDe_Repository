package com.pwde.app.data

import com.pwde.app.data.local.ControlJson
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.GestureAction
import com.pwde.app.data.model.VoiceShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun gestureSet_roundTripsAndKeepsNullDistinctFromEmpty() {
        val set = setOf(FacialGesture.SMILE, FacialGesture.NOD)
        assertEquals(set, ControlJson.decodeGestureSet(ControlJson.encodeGestureSet(set)))
        assertEquals(emptySet<FacialGesture>(), ControlJson.decodeGestureSet(ControlJson.encodeGestureSet(emptySet())))
        assertNull(ControlJson.encodeGestureSet(null))
        assertNull(ControlJson.decodeGestureSet(null))
        assertNull(ControlJson.decodeGestureSet("not json"))
        assertEquals(setOf(FacialGesture.NOD), ControlJson.decodeGestureSet("""["NOD","FROWN","NOT_A_GESTURE"]"""))
    }

    @Test
    fun isGestureEnabled_onlyTestedCuratedGesturesWhenTested() {
        assertTrue(ControlConfig().isGestureEnabled(FacialGesture.SMILE))
        val tested = ControlConfig(enabledGestures = setOf(FacialGesture.NOD))
        assertTrue(tested.isGestureEnabled(FacialGesture.NOD))
        assertFalse(tested.isGestureEnabled(FacialGesture.SMILE))
        // Raw blendshapes aren't part of the test.
        assertTrue(tested.isGestureEnabled(FacialGesture.MP_JAW_OPEN))
    }

    @Test
    fun conflictsFor_listsOtherActionsUsingTheSameGesture() {
        val config = ControlConfig(
            gestureAssignments = mapOf(
                GestureAction.RECENTER to FacialGesture.PUCKER,
                GestureAction.TOUCH_HOLD to FacialGesture.PUCKER,
                GestureAction.SELECT to FacialGesture.SMILE,
            ),
        )
        assertEquals(listOf(GestureAction.TOUCH_HOLD), config.conflictsFor(GestureAction.RECENTER, FacialGesture.PUCKER))
        assertTrue(config.conflictsFor(GestureAction.SELECT, FacialGesture.SMILE).isEmpty())
    }
}
