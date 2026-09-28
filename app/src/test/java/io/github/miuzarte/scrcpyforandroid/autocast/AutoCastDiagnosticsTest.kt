package io.github.miuzarte.scrcpyforandroid.autocast

import org.junit.Assert.*
import org.junit.Test

class AutoCastDiagnosticsTest {
    @Test fun screenCheckFailureRetainsMeasuredValuesAndItsOwnStage() {
        var saved = ""
        val trace = AutoCastDiagnostics { saved = it }
        trace.begin("version=test")
        trace.enter(AutoCastStep.F4)
        trace.note("fold", "committed=4 base=0")
        trace.enter(AutoCastStep.D1)
        trace.note("displays", "window=1; inner=1080x1920; cover=ON")
        val message = trace.failure(IllegalStateException("deadline"), timedOut = true)
        assertTrue(message.startsWith("D1：检查内外屏状态超时"))
        assertTrue(saved.contains("committed=4 base=0"))
        assertTrue(saved.contains("inner=1080x1920"))
        assertFalse(message.contains("首帧"))
        assertEquals(trace.report(), saved)
    }

    @Test fun reportRemainsBoundedAndKeepsVersionAndLatestFailure() {
        val trace = AutoCastDiagnostics()
        trace.begin("version=test")
        repeat(100) { trace.note("sample", "x".repeat(2_000)) }
        trace.enter(AutoCastStep.W1)
        trace.failure(IllegalStateException("launch failed"), timedOut = false)
        val report = trace.report()
        assertTrue(report.length <= 12_000)
        assertTrue(report.startsWith("Flip5 AutoCast\nversion=test"))
        assertTrue(report.contains("W1：重新打开外屏窗口失败"))
        assertTrue(report.endsWith("launch failed"))
    }
}
