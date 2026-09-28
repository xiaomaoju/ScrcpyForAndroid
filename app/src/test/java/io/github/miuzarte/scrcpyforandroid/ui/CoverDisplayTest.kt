package io.github.miuzarte.scrcpyforandroid.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverDisplayTest {
    @Test
    fun onlyCoverPanelEnablesCompactLayoutInEitherRotation() {
        assertTrue(isFlip5CoverDisplay(720, 748))
        assertTrue(isFlip5CoverDisplay(748, 720))
        assertFalse(isFlip5CoverDisplay(1080, 2640))
        assertFalse(isFlip5CoverDisplay(2640, 1080))
        assertFalse(isFlip5CoverDisplay(1080, 1080))
        assertFalse(isFlip5CoverDisplay(0, 0))
    }
}
