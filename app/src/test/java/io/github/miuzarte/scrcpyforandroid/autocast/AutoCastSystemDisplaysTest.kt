package io.github.miuzarte.scrcpyforandroid.autocast

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutoCastSystemDisplaysTest {
    private fun display(id: Int, width: Int, height: Int, state: String = "ON", type: String = "INTERNAL",
        enabled: Boolean = true, transition: Boolean = false): String = """
        Display $id:
          mDisplayId=$id
          mIsEnabled=$enabled
          mIsInTransition=$transition
          mBaseDisplayInfo=DisplayInfo{"Built-in Screen", displayId $id, displayGroupId 0, FLAG_TRUSTED, real $width x $height, state $state, committedState $state, type $type, uniqueId "private-id", app $width x $height}
    """.trimIndent()

    private val inner = display(0, 1080, 1920)

    @Test fun reportedFlip6PanelsResolveEvenWhenAppEnumerationHasNoCover() = runBlocking {
        val shellDisplays = AutoCastSystemDisplays.parse(inner + "\n" + display(1, 748, 720))
        val fold = AutoCastPolicy.FoldState(4, "CONCURRENT_INNER_DEFAULT")
        val snapshot = AutoCastPolicy.FoldSnapshot(fold, AutoCastPolicy.FoldState(0, "CLOSED"), fold)
        // Reproduce the asynchronous shell branch of W1 while app discovery returns null.
        val visibleAppCoverId: Int? = null
        val id = AutoCastDisplayWaiter.await(4, "missing", { snapshot }, {
            visibleAppCoverId ?: run { delay(1); AutoCastSystemDisplays.cover(shellDisplays)?.id }
        })
        assertEquals(1, id)
        assertFalse(AutoCastSystemDisplays.windowReady(shellDisplays, id, 0))
        assertTrue(AutoCastSystemDisplays.windowReady(shellDisplays, id, 1))
    }

    @Test fun acceptsDynamicIdsAndBothCoverOrientations() {
        for ((width, height) in listOf(720 to 748, 748 to 720)) {
            val displays = AutoCastSystemDisplays.parse(inner + "\n" + display(53, width, height))
            assertEquals(53, AutoCastSystemDisplays.cover(displays)?.id)
            assertTrue(AutoCastSystemDisplays.windowReady(displays, 53, 53))
        }
    }

    @Test fun requiresInternalNonDefaultCoverAndDoesNotGuessFromPhysicalRecords() {
        val physical = "DisplayDeviceInfo{\"cover\", 748 x 720, state ON, type INTERNAL}"
        assertNull(AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(physical)))
        assertNull(AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(display(0, 748, 720))))
        assertNull(AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(display(7, 748, 720, type = "VIRTUAL"))))
        assertNull(AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(display(7, 1080, 1920))))
    }

    @Test fun rejectsDuplicateOrAmbiguousCoverRecords() {
        val cover = display(7, 748, 720)
        assertNull(AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(inner + "\n" + cover + "\n" + cover)))
        assertNull(AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(cover + "\n" + display(9, 720, 748))))
    }

    @Test fun ignoresOverrideDimensionsAndState() {
        val dump = display(7, 748, 720) + "\n" +
            "mOverrideDisplayInfo=DisplayInfo{\"Built-in Screen\", displayId 7, real 800 x 600, state UNKNOWN, type INTERNAL, app 800 x 600}"
        val cover = AutoCastSystemDisplays.cover(AutoCastSystemDisplays.parse(dump))!!
        assertEquals(748, cover.width)
        assertEquals("ON", cover.state)
    }

    @Test fun missingMalformedAndMismatchedFieldsFailClosed() {
        val cover = display(7, 748, 720)
        for (invalid in listOf("", "permission denied", cover.replace("displayId 7", "displayId 9"),
            cover.replace("real 748 x 720", "real 0 x 720"), cover.replace("type INTERNAL", "missing-type"),
            cover.replace("state ON", "missing-state"), cover.replace("Display 7:", "No logical header:"))) {
            assertTrue(AutoCastSystemDisplays.parse(invalid).isEmpty())
        }
    }

    @Test fun rejectsPoweredOffDisabledAndTransitioningCoverReadiness() {
        for (cover in listOf(display(7, 748, 720, state = "OFF"), display(7, 748, 720, enabled = false),
            display(7, 748, 720, transition = true))) {
            val displays = AutoCastSystemDisplays.parse(inner + "\n" + cover)
            assertEquals(7, AutoCastSystemDisplays.cover(displays)?.id)
            assertFalse(AutoCastSystemDisplays.windowReady(displays, 7, 7))
        }
    }

    @Test fun rejectsWrongDestinationWrongWindowAndMissingInner() {
        val cover = display(7, 748, 720)
        val displays = AutoCastSystemDisplays.parse(inner + "\n" + cover)
        assertFalse(AutoCastSystemDisplays.windowReady(displays, 1, 7))
        assertFalse(AutoCastSystemDisplays.windowReady(displays, 7, 0))
        assertFalse(AutoCastSystemDisplays.windowReady(AutoCastSystemDisplays.parse(cover), 7, 7))
    }

    @Test fun freshMonitoringSnapshotRejectsRemovedOrOffCover() {
        assertTrue(AutoCastSystemDisplays.windowReady(AutoCastSystemDisplays.parse(inner + "\n" + display(7, 748, 720)), 7, 7))
        assertFalse(AutoCastSystemDisplays.windowReady(AutoCastSystemDisplays.parse(inner), 7, 7))
        assertFalse(AutoCastSystemDisplays.windowReady(AutoCastSystemDisplays.parse(inner + "\n" + display(7, 748, 720, state = "OFF")), 7, 7))
    }

    @Test fun summaryRetainsEnabledStateWithoutRawNamesOrIds() {
        val summary = AutoCastSystemDisplays.summary(AutoCastSystemDisplays.parse(inner + "\n" + display(7, 748, 720, enabled = false)))
        assertTrue(summary.contains("id=7, 748x720, state=ON, type=INTERNAL, enabled=false"))
        assertFalse(summary.contains("Built-in Screen"))
        assertFalse(summary.contains("private-id"))
    }
}
