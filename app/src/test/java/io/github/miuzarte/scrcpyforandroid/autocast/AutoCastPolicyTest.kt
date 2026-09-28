package io.github.miuzarte.scrcpyforandroid.autocast

import org.junit.Assert.*
import org.junit.Test

class AutoCastPolicyTest {
    @Test fun coverExitModeUsesItsMeaningInsteadOfAssumingOne() {
        val tent = AutoCastPolicy.FoldState(7, "TENT")
        val states = listOf(AutoCastPolicy.FoldState(0, "CLOSED"), tent,
            AutoCastPolicy.FoldState(4, "CONCURRENT_INNER_DEFAULT"))
        assertEquals(tent, AutoCastPolicy.coverOnlyState(states))
        assertNull(AutoCastPolicy.coverOnlyState(states.filter { it != tent }))
        assertNull(AutoCastPolicy.coverOnlyState(listOf(tent, tent.copy(id = 1))))
    }
    @Test fun disabledActivationStillRoutesAnExistingInnerModeThroughSessionCleanup() {
        assertTrue(AutoCastPolicy.routeMainThroughActivation(false, true, true, true, alreadyPrepared = true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(false, true, true, true, alreadyPrepared = false))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(false, true, true, false, alreadyPrepared = true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(false, true, false, true, alreadyPrepared = true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(false, false, true, true, alreadyPrepared = true))
    }

    @Test fun adoptingAStateRequiresTheNamedInnerModeAndAClosedPhysicalPhone() {
        val closed = AutoCastPolicy.FoldState(0, "CLOSED")
        val dual = AutoCastPolicy.FoldState(4, "CONCURRENT_INNER_DEFAULT")
        val opened = AutoCastPolicy.FoldState(3, "OPENED")
        assertTrue(AutoCastPolicy.preparedInner(AutoCastPolicy.FoldSnapshot(dual, closed, dual)))
        assertFalse(AutoCastPolicy.preparedInner(AutoCastPolicy.FoldSnapshot(closed, closed, null)))
        assertFalse(AutoCastPolicy.preparedInner(AutoCastPolicy.FoldSnapshot(dual, opened, dual)))
        assertFalse(AutoCastPolicy.preparedInner(AutoCastPolicy.FoldSnapshot(opened, closed, opened)))
    }
    @Test fun legacyExplicitInnerRequestSurvivesDisabledAutomaticPreparation() {
        assertTrue(AutoCastPolicy.shouldPrepareInner(true, false))
        assertFalse(AutoCastPolicy.shouldPrepareInner(false, false))
        assertTrue(AutoCastPolicy.shouldPrepareInner(false, true))
        assertTrue(AutoCastPolicy.shouldPrepareInner(true, true))
    }
    @Test fun manualStartActivatesOnlyForEnabledLocalFlip5Cover() {
        assertTrue(AutoCastPolicy.routeMainThroughActivation(true, true, true, true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(false, true, true, true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(true, false, true, true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(true, true, false, true))
        assertFalse(AutoCastPolicy.routeMainThroughActivation(true, true, true, false))
    }
    @Test fun onlyFlip5IsAdmitted() {
        assertTrue(AutoCastPolicy.supports("samsung", "SM-F7310"))
        assertTrue(AutoCastPolicy.supports("Samsung", "SM-F731B"))
        assertFalse(AutoCastPolicy.supports("samsung", "SM-F741B"))
        assertFalse(AutoCastPolicy.supports("Google", "SM-F731B"))
    }

    @Test fun dualStateIsResolvedByMeaningInsteadOfAssumingFour() {
        val states = AutoCastPolicy.states("""
            DeviceState{identifier=0, name='CLOSED', app_accessible=true}
            DeviceState{identifier=4, name='OPEN', app_accessible=true}
            DeviceState{identifier=8, name='CONCURRENT_INNER_DEFAULT', app_accessible=true}
        """.trimIndent())
        assertEquals(8, AutoCastPolicy.dualState(states)?.id)
        assertNull(AutoCastPolicy.dualState(states.take(2)))
    }

    @Test fun ambiguousModesFailClosed() {
        assertNull(AutoCastPolicy.dualState(listOf(AutoCastPolicy.FoldState(4, "DUAL_DISPLAY"),
            AutoCastPolicy.FoldState(5, "CONCURRENT_INNER_DEFAULT"))))
    }

    @Test fun physicalFoldStateIsDistinctFromRequestedState() {
        val snapshot = AutoCastPolicy.snapshot("""
            Committed state: DeviceState{identifier=4, name='DUAL_DISPLAY'}
            Base state: DeviceState{identifier=0, name='CLOSED'}
            Override state: DeviceState{identifier=4, name='DUAL_DISPLAY'}
        """.trimIndent())
        assertEquals(4, snapshot.current.id)
        assertTrue(AutoCastPolicy.isClosed(snapshot.base))
        assertEquals(4, snapshot.override?.id)
    }

    @Test fun unfoldedOrUnreadableStateCannotBeTreatedAsClosed() {
        val snapshot = AutoCastPolicy.snapshot("Committed state: DeviceState{identifier=2, name='OPEN'}")
        assertFalse(AutoCastPolicy.isClosed(snapshot.base))
        assertNull(snapshot.override)
        assertThrows(IllegalArgumentException::class.java) { AutoCastPolicy.snapshot("Permission denied") }
    }

    @Test fun rejectsCoverFeedbackAndVirtualDisplayEvenWithInnerSizedContent() {
        assertFalse(AutoCastPolicy.acceptsCapture(1, 1, 720, 748))
        assertFalse(AutoCastPolicy.acceptsCapture(0, 0, 1080, 2640))
        assertFalse(AutoCastPolicy.acceptsCapture(53, 1, 1080, 1920))
        assertFalse(AutoCastPolicy.acceptsCapture(0, -1, 1080, 2640))
        assertFalse(AutoCastPolicy.acceptsCapture(0, 1, 0, 0))
    }

    @Test fun currentSizeIsDynamicRatherThanAResolutionAllowList() {
        listOf(1080 to 1920, 1080 to 2640, 1920 to 1080, 720 to 1280, 1440 to 2560, 748 to 720).forEach { (width, height) ->
            assertTrue(AutoCastPolicy.displaysReady(0, width, height, 1, true, 1))
            assertTrue(AutoCastPolicy.acceptsCapture(0, 1, width, height))
        }
    }

    @Test fun pairingInputRequiresExactlySixAsciiDigits() {
        assertEquals("123456", AutoCastPolicy.pairingCode(" 123456 "))
        listOf("12345", "1234567", "123 456", "１２３４５６", "", "12345;").forEach {
            assertNull(AutoCastPolicy.pairingCode(it))
        }
    }

    @Test fun reportedSmF731U1SnapshotPassesTheActualPreparationGate() {
        // flip5.7 physical report: committed=4 INNER_DEFAULT, display 0 ON 1080x1920,
        // recognized cover 1 ON 748x720, window moved to 1; video had not started.
        assertTrue(AutoCastPolicy.displaysReady(sourceId = 0, width = 1080, height = 1920,
            coverId = 1, coverOn = true, windowId = 1))
        assertTrue(AutoCastPolicy.displaysReady(sourceId = 0, width = 1920, height = 1080,
            coverId = 1, coverOn = true, windowId = 1))
    }

    @Test fun preparationStillRejectsAnInnerWindowDarkCoverOrFeedbackSource() {
        assertFalse(AutoCastPolicy.displaysReady(0, 1080, 1920, 1, true, 0))
        assertFalse(AutoCastPolicy.displaysReady(0, 1080, 1920, 1, false, 1))
        assertFalse(AutoCastPolicy.displaysReady(0, 1080, 1920, 1, true, 53))
        assertFalse(AutoCastPolicy.displaysReady(1, 748, 720, 1, true, 1))
        assertFalse(AutoCastPolicy.displaysReady(53, 1080, 1920, 1, true, 1))
        assertFalse(AutoCastPolicy.displaysReady(0, 0, 0, 1, true, 1))
    }

    @Test fun captureRequiresTheInnerDefaultModeToSurviveWindowRelaunch() {
        val closed = AutoCastPolicy.FoldState(0, "CLOSED")
        val dual = AutoCastPolicy.FoldState(4, "CONCURRENT_INNER_DEFAULT")
        assertTrue(AutoCastPolicy.innerDefaultActive(AutoCastPolicy.FoldSnapshot(dual, closed, dual), 4))
        assertFalse(AutoCastPolicy.innerDefaultActive(AutoCastPolicy.FoldSnapshot(closed, closed, null), 4))
        val opened = AutoCastPolicy.FoldState(3, "OPENED")
        assertFalse(AutoCastPolicy.innerDefaultActive(AutoCastPolicy.FoldSnapshot(dual, opened, dual), 4))
    }
}
