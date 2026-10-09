package io.github.miuzarte.scrcpyforandroid.autocast

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AutoCastDisplayWaiterTest {
    private val closed = AutoCastPolicy.FoldState(0, "CLOSED")
    private val dual = AutoCastPolicy.FoldState(4, "CONCURRENT_INNER_DEFAULT")
    private val active = AutoCastPolicy.FoldSnapshot(dual, closed, dual)

    @Test fun readyCoverReturnsImmediatelyWithoutAnotherPoll() = runBlocking {
        var reads = 0
        assertEquals(7, AutoCastDisplayWaiter.await(4, "missing", { reads++; active }, { 7 }))
        assertEquals(1, reads)
    }

    @Test(timeout = 5_000) fun coverAppearingAfterOldOneSecondDeadlineIsAccepted() = runBlocking {
        var cover: Int? = null
        val appearance = launch { delay(1_200); cover = 7 }
        assertEquals(7, AutoCastDisplayWaiter.await(4, "missing", { active }, { cover }, timeoutMs = 3_000, pollMs = 20))
        appearance.join()
    }

    @Test(timeout = 5_000) fun missingCoverStopsWithDiscoveryReason() = runBlocking {
        val error = runCatching { AutoCastDisplayWaiter.await<Int>(4, "cover never appeared", { active }, { null }, timeoutMs = 40, pollMs = 5) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals("cover never appeared", error?.message)
    }

    @Test fun foldExitRejectsEvenAnAvailableCover() = runBlocking {
        var sampled = false
        val error = runCatching {
            AutoCastDisplayWaiter.await(4, "missing", { active.copy(current = closed, override = null) }, { sampled = true; 7 })
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("双屏模式已退出"))
        assertFalse(sampled)
    }

    @Test fun unfoldingDuringWaitStopsBeforeAcceptingCover() = runBlocking {
        var reads = 0
        val error = runCatching {
            AutoCastDisplayWaiter.await<Int>(4, "missing", {
                if (++reads == 1) active else active.copy(base = AutoCastPolicy.FoldState(3, "OPENED"))
            }, { null }, pollMs = 1)
        }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("手机已展开"))
        assertEquals(2, reads)
    }

    @Test(timeout = 5_000) fun slowFoldReadIsBoundedByDisplayDeadline() = runBlocking {
        val error = runCatching {
            AutoCastDisplayWaiter.await(4, "fold read exceeded wait", { delay(1_000); active }, { 7 }, timeoutMs = 40)
        }.exceptionOrNull()
        assertEquals("fold read exceeded wait", error?.message)
    }

    @Test fun stoppingPropagatesCancellationInsteadOfReportingMissingCover() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val waiting = async {
            AutoCastDisplayWaiter.await<Int>(4, "missing", { active }, { started.complete(Unit); null })
        }
        started.await()
        waiting.cancelAndJoin()
        assertTrue(runCatching { waiting.await() }.exceptionOrNull() is CancellationException)
    }

    @Test fun windowReadinessWaitsForCoverOnAndCorrectWindow() = runBlocking {
        var samples = 0
        AutoCastDisplayWaiter.await(4, "window never arrived", { active }, {
            samples++
            Unit.takeIf { AutoCastPolicy.displaysReady(0, 1080, 1920, 7, samples >= 2, if (samples >= 3) 7 else 0) }
        }, pollMs = 1)
        assertEquals(3, samples)
    }

    @Test(timeout = 5_000) fun wrongWindowHasItsOwnFailureReason() = runBlocking {
        val error = runCatching {
            AutoCastDisplayWaiter.await(4, "window never arrived", { active }, {
                Unit.takeIf { AutoCastPolicy.displaysReady(0, 1080, 1920, 7, true, 0) }
            }, timeoutMs = 40, pollMs = 5)
        }.exceptionOrNull()
        assertEquals("window never arrived", error?.message)
    }
}
