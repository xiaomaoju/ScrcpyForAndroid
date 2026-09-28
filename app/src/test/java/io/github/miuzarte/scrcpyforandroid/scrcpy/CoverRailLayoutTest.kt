package io.github.miuzarte.scrcpyforandroid.scrcpy

import org.junit.Assert.*
import org.junit.Test

class CoverRailLayoutTest {
    @Test fun wideSourcesUseRailWhileNarrowPortraitKeepsThePanel() {
        assertFalse(coverNeedsControlRail(1080, 1920, 338f, 352f))
        assertFalse(coverNeedsControlRail(1080, 2640, 338f, 352f))
        assertTrue(coverNeedsControlRail(1080, 1080, 338f, 352f))
        assertTrue(coverNeedsControlRail(1920, 1080, 338f, 352f))
    }

    @Test fun boundaryChangesNeedToCrossTheDeadBand() {
        // A square video at height 200 leaves width - 204 dp for the controls.
        assertFalse(coverNeedsControlRail(1000, 1000, 320f, 200f, false))
        assertTrue(coverNeedsControlRail(1000, 1000, 320f, 200f, true))
        assertTrue(coverNeedsControlRail(1000, 1000, 310f, 200f, false))
        assertFalse(coverNeedsControlRail(1000, 1000, 335f, 200f, true))
    }

    @Test fun railVideoFitsCompletelyAndMapsItsCorners() {
        val viewportWidth = 720 - 85 - 8
        for ((width, height) in listOf(1080 to 1080, 1920 to 1080, 1080 to 1920)) {
            val bounds = fitVideoContent(width, height, viewportWidth, 748)
            assertTrue(bounds.left >= 0 && bounds.top >= 0)
            assertTrue(bounds.left + bounds.width <= viewportWidth + .001f)
            assertTrue(bounds.top + bounds.height <= 748.001f)
            assertEquals(width.toFloat() / height, bounds.width / bounds.height, .0001f)
            assertEquals(0 to 0, bounds.toVideoPosition(bounds.left, bounds.top, width, height))
            assertEquals(width - 1 to height - 1, bounds.toVideoPosition(bounds.left + bounds.width, bounds.top + bounds.height, width, height))
        }
    }
}
