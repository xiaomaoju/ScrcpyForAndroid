package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
class CoverAdaptiveInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun label(id: Int) = context.getString(id)
    private fun click(id: Int) = compose.onNodeWithContentDescription(label(id)).assertIsDisplayed().performTouchInput { click() }
    private fun choose(id: Int) = compose.onNodeWithText(label(id)).assertIsDisplayed().performTouchInput { click() }

    @Test fun wideRailHasFiveOrderedKeysAndDrawersDoNotResizeTheVideo() {
        var exited = false
        compose.setContent {
            val scrcpy = remember { Scrcpy(context) }
            CompositionLocalProvider(LocalCoverDisplay provides true, LocalDensity provides Density(2f, 1f)) {
                MiuixTheme {
                    Box(Modifier.size(330.dp, 300.dp)) {
                        CoverControlScreen(scrcpy, Scrcpy.Session.SessionInfo("test", 0, null, 1920, 1080, controlEnabled = true),
                            listOf("设备"), {}, { exited = true }) { Text("Native software") }
                    }
                }
            }
        }
        compose.onNodeWithTag("cover-control-rail").assertIsDisplayed()
        val cover = compose.onNodeWithTag("cover-control").fetchSemanticsNode().boundsInRoot
        val keys = listOf(R.string.cover_exit, R.string.cover_tools, R.string.cover_remote_back, R.string.cover_remote_home, R.string.cover_remote_recent)
            .map { compose.onNodeWithContentDescription(label(it)).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        keys.forEach { assertTrue(it.top >= cover.top && it.bottom <= cover.bottom && it.right <= cover.right) }
        assertTrue(keys.zipWithNext().all { (a, b) -> a.bottom <= b.top })
        val image = compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot
        assertEquals(1920f / 1080f, image.width / image.height, .01f)
        click(R.string.cover_tools)
        choose(R.string.cover_mouse_pad)
        compose.onNodeWithTag("cover-trackpad").assertIsDisplayed().performTouchInput { swipeUp() }
        assertEquals(image, compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot)
        click(R.string.cover_close_panel)
        compose.onNodeWithTag("cover-trackpad").assertDoesNotExist()
        click(R.string.cover_tools)
        choose(R.string.cover_details)
        compose.onNodeWithText("Native software").assertIsDisplayed()
        assertEquals(image, compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot)
        click(R.string.cover_close_panel)
        click(R.string.cover_tools)
        choose(R.string.cover_two_pane)
        compose.onNodeWithTag("cover-control-rail").assertDoesNotExist()
        click(R.string.cover_details)
        choose(R.string.cover_auto_layout)
        compose.onNodeWithTag("cover-control-rail").assertIsDisplayed()
        click(R.string.cover_exit)
        compose.runOnIdle { assertTrue(exited) }
    }

    @Test fun aspectChangesKeepTheSoftwareStateAndTouchMode() {
        val session = mutableStateOf(Scrcpy.Session.SessionInfo("test", 0, null, 1080, 1920, controlEnabled = true))
        var mounts = 0
        var disposals = 0
        compose.setContent {
            val scrcpy = remember { Scrcpy(context) }
            CompositionLocalProvider(LocalCoverDisplay provides true, LocalDensity provides Density(2f, 1f)) {
                MiuixTheme {
                    Box(Modifier.size(330.dp, 300.dp)) {
                        CoverControlScreen(scrcpy, session.value, listOf("设备"), {}, {}) {
                            DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                            var count by remember { mutableIntStateOf(0) }
                            Text("count:$count", Modifier.fillMaxWidth().height(48.dp).clickable { count++ }.testTag("software-counter"))
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("software-counter").performTouchInput { click() }
        compose.runOnIdle { session.value = session.value.copy(width = 1080, height = 1080) }
        compose.onNodeWithTag("cover-control-rail").assertIsDisplayed()
        click(R.string.cover_tools)
        choose(R.string.cover_details)
        compose.onNodeWithText("count:1").assertIsDisplayed()
        compose.onNodeWithTag("software-counter").performTouchInput { click() }
        compose.runOnIdle { session.value = session.value.copy(width = 1080, height = 2640) }
        compose.onNodeWithTag("cover-control-rail").assertDoesNotExist()
        compose.onNodeWithTag("cover-trackpad").assertDoesNotExist()
        compose.onNodeWithText("count:2").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, mounts); assertEquals(0, disposals) }
    }

    @Test fun shortRailRetainsExitAndDisablesRemoteKeysWhenInteractionIsBlocked() {
        var exited = false
        compose.setContent {
            val scrcpy = remember { Scrcpy(context) }
            CompositionLocalProvider(LocalCoverDisplay provides true, LocalDensity provides Density(2f, 1f)) {
                MiuixTheme {
                    Box(Modifier.size(250.dp, 135.dp)) {
                        CoverControlScreen(scrcpy, Scrcpy.Session.SessionInfo("test", 0, null, 1920, 1080, controlEnabled = true),
                            listOf("设备"), {}, { exited = true }, exitLabel = "结束专用会话", interactionEnabled = false) { Text("Native software") }
                    }
                }
            }
        }
        val rail = compose.onNodeWithTag("cover-control-rail").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        listOf(R.string.cover_remote_back, R.string.cover_remote_home, R.string.cover_remote_recent).forEach {
            val bounds = compose.onNodeWithContentDescription(label(it)).assertIsDisplayed().assertIsNotEnabled().fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.bottom <= rail.bottom)
        }
        compose.onNodeWithContentDescription("结束专用会话").performTouchInput { click() }
        compose.runOnIdle { assertTrue(exited) }
    }
}
