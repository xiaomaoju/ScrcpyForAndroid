package io.github.miuzarte.scrcpyforandroid.scrcpy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoContentBoundsTest {
    @Test
    fun portraitInnerScreenFitsCoverWithoutCropping() {
        val bounds = fitVideoContent(1080, 2640, 720, 748)
        assertEquals(306f, bounds.width, 0.001f)
        assertEquals(748f, bounds.height, 0.001f)
        assertEquals(207f, bounds.left, 0.001f)
        assertEquals(0f, bounds.top, 0.001f)
        assertEquals(0 to 0, bounds.toVideoPosition(207f, 0f, 1080, 2640))
        assertEquals(1079 to 2639, bounds.toVideoPosition(513f, 748f, 1080, 2640))
    }

    @Test
    fun landscapeSourceFitsCoverWidth() {
        val bounds = fitVideoContent(2640, 1080, 720, 748)
        assertEquals(720f, bounds.width, 0.001f)
        assertEquals(294.54545f, bounds.height, 0.001f)
        assertEquals(0f, bounds.left, 0.001f)
        assertEquals(1320 to 540, bounds.toVideoPosition(360f, 374f, 2640, 1080))
    }

    @Test
    fun safeAreaAndControlsUseRemainingViewportForBothRenderingAndTouch() {
        // A reduced viewport models cutout, navigation/IME and a reserved control bar.
        val bounds = fitVideoContent(1080, 2640, 660, 540)
        assertEquals(540f, bounds.height, 0.001f)
        assertTrue(bounds.left > 0f)
        assertEquals(0 to 0, bounds.toVideoPosition(bounds.left, bounds.top, 1080, 2640))
        assertEquals(1079 to 2639, bounds.toVideoPosition(bounds.left + bounds.width, bounds.top + bounds.height, 1080, 2640))
        assertEquals(0 to 0, bounds.toVideoPosition(-100f, -100f, 1080, 2640))
    }

    @Test
    fun unchangedInnerViewportAndEmptyFrames() {
        assertEquals(VideoContentBounds(1080f, 2640f, 0f, 0f), fitVideoContent(1080, 2640, 1080, 2640))
        assertEquals(VideoContentBounds(0f, 0f, 0f, 0f), fitVideoContent(0, 0, 720, 748))
        assertEquals(VideoContentBounds(0f, 0f, 0f, 0f), fitVideoContent(1080, 2640, 0, 0))
    }
}
