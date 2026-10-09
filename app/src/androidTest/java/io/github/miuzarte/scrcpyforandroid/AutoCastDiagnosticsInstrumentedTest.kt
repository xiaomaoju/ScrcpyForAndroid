package io.github.miuzarte.scrcpyforandroid

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.miuzarte.scrcpyforandroid.autocast.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCastDiagnosticsInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failureReportCanBeCopiedFromTheCoverStatusPage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trace = AutoCastDiagnostics()
        trace.begin("version=remote-test; model=SM-F741N")
        trace.enter(AutoCastStep.W1)
        trace.note("display wait result", "stage=W1; samples=34; window=0; id=0, state=2, mode=1080x1920")
        trace.note("fold at display failure", "committed=4; base=0; override=4")
        trace.note("system displays", "device: 1080 x 1920, state ON; logical: displayId 0, 1080 x 1920, state ON")
        val message = trace.failure(IllegalStateException("双屏切换后等待 5 秒仍未找到外屏。"), false)
        val report = trace.report()
        compose.setContent {
            MaterialTheme {
                Surface {
                    AutoCastStatusScreen(AutoCastState(AutoCastPhase.ERROR, message, report), {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("autocast-copy-diagnostics").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText(context.getString(R.string.autocast_diagnostics_copied)).assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(report, context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString())
        }
    }
}
