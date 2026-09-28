package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.miuzarte.scrcpyforandroid.pages.CoverControlScreen
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class CoverControlInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun trackpadKeepsMovingAfterASecondFingerLiftsWithoutANewDown() {
        compose.setContent {
            val scrcpy = remember { Scrcpy(context) }
            CompositionLocalProvider(LocalCoverDisplay provides true, LocalDensity provides Density(2f, 1f)) {
                MiuixTheme {
                    Box(Modifier.size(330.dp, 300.dp)) {
                        CoverControlScreen(scrcpy, Scrcpy.Session.SessionInfo("test", 0, null, 1080, 1920, controlEnabled = true),
                            listOf("设备"), {}, {}) { Text("Software sentinel") }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.cover_switch_mouse)).performClick()
        val pad = compose.onNodeWithTag("cover-trackpad")
        val initial = cursorCenter()
        pad.performTouchInput {
            down(0, Offset(width * .2f, height * .2f))
            moveTo(0, Offset(width * .3f, height * .3f))
        }
        val moved = cursorCenter()
        assertTrue("Single-finger movement reaches the visible cursor", moved.x > initial.x + 5f)
        pad.performTouchInput {
            down(1, Offset(width * .7f, height * .7f))
            up(1)
        }
        val afterLift = cursorCenter()
        assertEquals("Lifting a second finger must not jump the cursor", moved.x, afterLift.x, 1f)
        pad.performTouchInput { moveTo(0, Offset(width * .4f, height * .4f)) }
        val resumed = cursorCenter()
        pad.performTouchInput { up(0) }
        assertTrue("The original finger must keep moving the cursor without another tap", resumed.x > afterLift.x + 5f)
        assertTrue(resumed.y > afterLift.y + 5f)
    }

    /** Read the actual white cursor drawn over the empty black video surface. */
    private fun cursorCenter(): Offset {
        val pixels = compose.onNodeWithTag("cover-video").captureToImage().toPixelMap()
        var count = 0
        var xTotal = 0f
        var yTotal = 0f
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val color = pixels[x, y]
            if (color.red > .9f && color.green > .9f && color.blue > .9f) {
                count++; xTotal += x; yTotal += y
            }
        }
        assertTrue("The local cursor is rendered", count > 5)
        return Offset(xTotal / count, yTotal / count)
    }

    @Test fun inputModesKeepSoftwareComposedAndStoppedSessionDisablesRemoteKeys() {
        val session = mutableStateOf<Scrcpy.Session.SessionInfo?>(Scrcpy.Session.SessionInfo("test", 0, null, 1080, 1920, controlEnabled = true))
        var mounted = 0
        var disposed = 0
        compose.setContent {
            val scrcpy = remember { Scrcpy(context) }
            CompositionLocalProvider(LocalCoverDisplay provides true, LocalDensity provides Density(2.75f, 1.3f)) {
                MiuixTheme {
                    Box(Modifier.size(250.dp, 210.dp)) {
                        CoverControlScreen(scrcpy, session.value, listOf("Devices", "Terminal", "Files", "Settings"), {}, {}) {
                            DisposableEffect(Unit) { mounted++; onDispose { disposed++ } }
                            Text("Software sentinel")
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("cover-trackpad").assertDoesNotExist()
        compose.onNodeWithTag("cover-direct-touch").assertIsDisplayed()
        compose.onNodeWithText("Software sentinel").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.cover_switch_mouse)).performClick()
        compose.onNodeWithTag("cover-trackpad").assertIsDisplayed()
        val image = compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot
        val pane = compose.onNodeWithTag("cover-video").fetchSemanticsNode().boundsInRoot
        assertEquals(1080f / 1920f, image.width / image.height, .01f)
        assertTrue(image.left >= pane.left - 1 && image.right <= pane.right + 1)
        assertTrue(image.top >= pane.top - 1 && image.bottom <= pane.bottom + 1)
        compose.onNodeWithContentDescription(context.getString(R.string.cover_switch_touch)).performClick()
        compose.onNodeWithTag("cover-trackpad").assertDoesNotExist()
        compose.onNodeWithText("Software sentinel").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.cover_switch_mouse)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.cover_details)).performClick()
        compose.onNodeWithText("Software sentinel").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, mounted); assertEquals(0, disposed); session.value = null }
        compose.onNodeWithContentDescription(context.getString(R.string.cover_remote_home)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.cover_stopped)).assertIsDisplayed()
    }

    @Test fun shortKeyboardViewportKeepsAllControlsInsideCover() {
        compose.setContent {
            val scrcpy = remember { Scrcpy(context) }
            CompositionLocalProvider(LocalCoverDisplay provides true, LocalDensity provides Density(2.75f, 1.3f)) {
                MiuixTheme {
                    Box(Modifier.size(250.dp, 135.dp)) {
                        CoverControlScreen(scrcpy, Scrcpy.Session.SessionInfo("test", 0, null, 1080, 2640, controlEnabled = true),
                            listOf("Devices", "Terminal", "Files", "Settings"), {}, {}) { Text("Software sentinel") }
                    }
                }
            }
        }
        val cover = compose.onNodeWithTag("cover-control").fetchSemanticsNode().boundsInRoot
        val image = compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot
        assertEquals(1080f / 2640f, image.width / image.height, .01f)
        assertTrue(image.top >= cover.top - 1 && image.bottom <= cover.bottom + 1)
        listOf(R.string.cover_remote_back, R.string.cover_remote_home, R.string.cover_remote_recent).forEach {
            val bounds = compose.onNodeWithContentDescription(context.getString(it)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.bottom <= cover.bottom + 1)
        }
    }
}
