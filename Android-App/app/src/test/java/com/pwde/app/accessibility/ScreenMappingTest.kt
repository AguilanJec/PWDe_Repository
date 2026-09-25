package com.pwde.app.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenMappingTest {
    @Test
    fun fractionsScaleToTheWholeScreen() {
        assertEquals(1200f to 540f, ScreenMapping.toPixels(0.5f, 0.5f, 2400, 1080))
        assertEquals(1800f to 270f, ScreenMapping.toPixels(0.75f, 0.25f, 2400, 1080))
    }

    @Test
    fun edgesStayOnScreen() {
        assertEquals(0f to 0f, ScreenMapping.toPixels(-0.2f, 0f, 2400, 1080))
        assertEquals(2399f to 1079f, ScreenMapping.toPixels(1f, 1.3f, 2400, 1080))
    }
}
