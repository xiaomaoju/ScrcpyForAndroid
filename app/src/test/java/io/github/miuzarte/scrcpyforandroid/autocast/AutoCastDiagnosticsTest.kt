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
        trace.enter(AutoCastStep.W2)
        trace.failure(IllegalStateException("launch failed"), timedOut = false)
        val report = trace.report()
        assertTrue(report.length <= 12_000)
        assertTrue(report.startsWith("Flip5 AutoCast\nversion=test"))
        assertTrue(report.contains("W2：重新打开外屏窗口失败"))
        assertTrue(report.endsWith("launch failed"))
    }

    @Test fun displayDumpKeepsBothPanelsButExcludesNamesAddressesAndUniqueIds() {
        val summary = AutoCastDiagnostics.displayDumpSummary("""
            DisplayDeviceInfo{"Private device", uniqueId="local:12345", 1080 x 1920, state ON, type INTERNAL, FLAG_SECURE}
            DisplayDeviceInfo{"Private cover", uniqueId="local:67890", 720 x 748, state OFF, type INTERNAL, address {port=2}, FLAG_PRIVATE}
            mBaseDisplayInfo=DisplayInfo{"Private device", displayId 0, real 1080 x 1920, state ON, ownerPackageName=private.package}
            mBaseDisplayInfo=DisplayInfo{"Private cover", displayId 7, real 720 x 748, state OFF}
        """.trimIndent())
        assertTrue(summary.contains("1080 x 1920"))
        assertTrue(summary.contains("720 x 748"))
        assertTrue(summary.contains("displayId 7"))
        assertTrue(summary.contains("state OFF"))
        assertTrue(summary.contains("FLAG_PRIVATE"))
        listOf("Private", "local:", "address", "ownerPackage", "private.package").forEach { assertFalse(summary.contains(it)) }
    }

    @Test fun remoteReportRetainsDiscoveryLaunchAndFailureWithElapsedTimes() {
        val trace = AutoCastDiagnostics()
        trace.begin("version=remote-test")
        trace.enter(AutoCastStep.W1)
        trace.note("display transition", "window=0; id=0")
        trace.enter(AutoCastStep.W2)
        trace.note("cover launch result", "Status: ok")
        trace.enter(AutoCastStep.D1)
        trace.failure(IllegalStateException("window not ready"), false)
        val report = trace.report()
        assertTrue(report.contains("W1 等待外屏出现"))
        assertTrue(report.contains("W2 重新打开外屏窗口"))
        assertTrue(report.contains("D1：检查内外屏状态失败"))
        assertTrue(Regex("\\[\\+\\d+ms] display transition:").containsMatchIn(report))
    }

    @Test fun verboseDisplayFlagsCannotTruncateTheLastLogicalCoverRecord() {
        val flags = "FLAG_ALLOWED_TO_BE_DEFAULT_DISPLAY, FLAG_ROTATES_WITH_CONTENT, FLAG_SECURE, FLAG_SUPPORTS_PROTECTED_BUFFERS, FLAG_TRUSTED, FLAG_PRIVATE, FLAG_OWN_CONTENT_ONLY"
        val dump = (0..2).joinToString("\n") { "DisplayDeviceInfo{\"panel\", 720 x 748, state ON, type INTERNAL, $flags}" } + "\n" +
            (0..2).joinToString("\n") { "mBaseDisplayInfo=DisplayInfo{\"panel\", displayId $it, real 720 x 748, state ON, type INTERNAL, $flags}" }
        val summary = AutoCastDiagnostics.displayDumpSummary(dump)
        assertTrue(summary.length <= 900)
        assertTrue(summary.contains("logical: displayId 2, 720 x 748, state ON"))
        val trace = AutoCastDiagnostics()
        trace.begin("version=test")
        trace.note("system displays", summary)
        assertTrue(trace.report().contains(summary))
    }
}
