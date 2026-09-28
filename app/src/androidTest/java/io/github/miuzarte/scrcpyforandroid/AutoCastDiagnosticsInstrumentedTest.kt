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
        val report = "version=test\nD1: window=1; inner=1080x1920; cover=ON"
        compose.setContent {
            MaterialTheme {
                Surface {
                    AutoCastStatusScreen(AutoCastState(AutoCastPhase.ERROR, "D1：检查内外屏状态超时。", report), {}, {}, {}, {})
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
