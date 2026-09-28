package io.github.miuzarte.scrcpyforandroid

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt in only on a disposable 720x748 emulator with its local adbd listening on 5555. */
@RunWith(AndroidJUnit4::class)
class CoverLoopbackInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun prepareDedicatedCover() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coverLoopback") == "true")
        assumeTrue(Build.HARDWARE.contains("ranchu"))
        assumeTrue(compose.activity.display?.mode?.physicalWidth == 720)
        // The system keyboard belongs to a different process and can outlive a test Activity.
        compose.runOnIdle {
            compose.activity.currentFocus?.clearFocus()
            compose.activity.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .hideSoftInputFromWindow(compose.activity.window.decorView.windowToken, 0)
        }
        compose.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        compose.waitUntil(5_000) { compose.onNodeWithTag("cover-address").isDisplayed() }
    }

    @Test(timeout = 60_000) fun connectionAndStartStayVisibleWhileParametersScroll() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coverLoopback") == "true")
        assumeTrue(Build.HARDWARE.contains("ranchu"))
        assumeTrue(compose.activity.display?.mode?.physicalWidth == 720)
        val runtime = AppRuntime.obtainSession(Scrcpy.SessionConfig())
        val controller = runtime.services.connectionController
        val originalOptions = Storage.scrcpyOptions.bundleState.value
        try {
            runBlocking(Dispatchers.IO) {
                Storage.scrcpyOptions.saveBundle(originalOptions.copy(newDisplay = "1080x1920/240", startApp = "com.android.settings", audio = true, fullscreen = false, maxFps = "15", clipboardAutosync = false))
            }
            compose.onNodeWithTag("cover-address").assertIsDisplayed().performTextReplacement("127.0.0.1:5555")
            compose.onNodeWithTag("cover-address").performImeAction()
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("main-tab-Devices").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("cover-start").assertIsDisplayed().assertIsNotEnabled()
            capture("disconnected-controls")
            compose.onNodeWithTag("cover-connect").performTouchInput { click() }
            waitEnabled("cover-start")
            compose.onNodeWithTag("cover-control").assertDoesNotExist()
            compose.onNodeWithTag("cover-start").assertIsDisplayed()
            capture("idle-before-touch")
            val controlsBefore = compose.onNodeWithTag("cover-connection").fetchSemanticsNode().boundsInRoot
            val list = compose.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst()
            val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            fun swipeList() = list.performTouchInput { swipe(Offset(width * .45f, height * .76f), Offset(width * .45f, height * .3f), 500) }
            swipeList()
            capture("idle-after-swipe")
            val after = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertTrue("A real upward swipe must move the connected device list: $before -> $after", after > before)
            assertEquals(controlsBefore, compose.onNodeWithTag("cover-connection").fetchSemanticsNode().boundsInRoot)
            var swipes = 0
            while (!compose.onNodeWithText(label(R.string.device_config_audio_forwarding)).isDisplayed() && swipes++ < 8) swipeList()
            compose.onNodeWithText(label(R.string.device_config_audio_forwarding)).assertIsDisplayed().performTouchInput { click() }
            compose.waitUntil(3_000) { !Storage.scrcpyOptions.bundleState.value.audio }
            compose.onNodeWithTag("cover-start").assertIsDisplayed().performTouchInput { click() }
            compose.waitUntil(20_000) { runtime.scrcpy.currentSessionState.value?.width == 1080 }
            assertEquals(1080, runtime.scrcpy.currentSessionState.value?.width)
            compose.onNodeWithTag("cover-control").assertDoesNotExist()
            capture("started-by-touch")
        } finally {
            runBlocking { withContext(Dispatchers.Main) { controller.disconnectAdbConnection() } }
            runBlocking { Storage.scrcpyOptions.saveBundle(originalOptions) }
        }
    }

    @Test(timeout = 60_000) fun actualSessionRendersAndOriginalSoftwarePagesRemainReachable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coverLoopback") == "true")
        assumeTrue(Build.HARDWARE.contains("ranchu"))
        val mode = compose.activity.display?.mode
        assumeTrue(mode?.physicalWidth == 720 && mode.physicalHeight == 748)
        val runtime = AppRuntime.obtainSession(Scrcpy.SessionConfig())
        val controller = runtime.services.connectionController
        val originalOptions = Storage.scrcpyOptions.bundleState.value
        try {
            runBlocking(Dispatchers.IO) {
                Storage.scrcpyOptions.saveBundle(originalOptions.copy(newDisplay = "1080x1920/240", startApp = "com.android.settings", audio = false, fullscreen = false, maxFps = "15", clipboardAutosync = false))
                controller.connectWithTimeout("127.0.0.1", 5555, 10_000)
                controller.handleAdbConnected("127.0.0.1", 5555)
                controller.updateQuickConnected(true)
            }
            compose.onNodeWithTag("cover-control").assertDoesNotExist()
            waitEnabled("cover-start")
            compose.onNodeWithTag("cover-start").assertIsDisplayed().performTouchInput { click() }
            compose.waitUntil(20_000) { runtime.scrcpy.currentSessionState.value?.width == 1080 }
            val barTypes = listOf(WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars())
            val originalBars = barTypes.associateWith { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(it) }
            waitEnabled("cover-open-fullscreen")
            compose.onNodeWithTag("cover-open-fullscreen").performTouchInput { click() }
            compose.waitUntil(20_000) { runCatching { compose.onAllNodesWithTag("cover-control").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.waitUntil(5_000) { barTypes.all { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(it) == false } }
            val session = requireNotNull(runtime.scrcpy.currentSessionState.value)
            assertEquals(1080, session.width)
            assertEquals(1920, session.height)
            compose.onNodeWithTag("cover-trackpad").assertDoesNotExist()
            compose.onNodeWithTag("cover-direct-touch").assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.cover_switch_mouse)).performTouchInput { click() }
            compose.onNodeWithTag("cover-trackpad").assertIsDisplayed()
            capture("mouse")
            compose.onNodeWithContentDescription(label(R.string.cover_switch_touch)).performTouchInput { click() }
            compose.onNodeWithTag("cover-trackpad").assertDoesNotExist()
            compose.onNodeWithTag("cover-direct-touch").performTouchInput { swipeUp(durationMillis = 700) }
            capture("touch-details")
            val list = compose.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).onFirst()
            // The page deliberately preserves its scroll position from the original host.
            list.performScrollToIndex(0)
            val before = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            compose.onNodeWithTag("cover-software").performTouchInput { swipeUp(durationMillis = 600) }
            assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > before)
            capture("details-after-swipe")
            compose.onNodeWithContentDescription(label(R.string.cover_details)).performTouchInput { click() }
            compose.onNodeWithText(label(R.string.main_tab_settings)).performTouchInput { click() }
            compose.onNodeWithText(label(R.string.main_tab_settings)).assertIsDisplayed()
            capture("settings")
            compose.onNodeWithContentDescription(label(R.string.cover_remote_back)).performTouchInput { click() }
            assertTrue(runtime.scrcpy.currentSessionState.value != null)
            compose.onNodeWithContentDescription(label(R.string.cover_exit)).performTouchInput { click() }
            compose.onNodeWithTag("cover-control").assertDoesNotExist()
            assertEquals(session.controlSessionId, runtime.scrcpy.currentSessionState.value?.controlSessionId)
            compose.onNodeWithTag("cover-open-fullscreen").assertIsDisplayed()
            compose.waitUntil(5_000) { barTypes.all { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(it) == originalBars[it] } }
            capture("returned-original")
        } finally {
            runBlocking { withContext(Dispatchers.Main) { controller.disconnectAdbConnection() } }
            runBlocking { Storage.scrcpyOptions.saveBundle(originalOptions) }
        }
    }

    @Test(timeout = 60_000) fun terminalFilesAndPairingKeepPrimaryActionsVisible() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coverLoopback") == "true")
        assumeTrue(Build.HARDWARE.contains("ranchu"))
        val controller = AppRuntime.obtainSession(Scrcpy.SessionConfig()).services.connectionController
        try {
            // A prior system keyboard can outlive the previous test activity.
            if (ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("main-tab-Devices").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(label(R.string.device_pairing_title)).assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithText(label(R.string.button_cancel)).assertIsDisplayed()
            compose.onNodeWithText(label(R.string.label_wlan_pairing_code)).assertIsDisplayed()
            capture("pairing-form")
            compose.onNodeWithText(label(R.string.button_cancel)).performTouchInput { click() }
            runBlocking(Dispatchers.IO) {
                controller.connectWithTimeout("127.0.0.1", 5555, 10_000)
                controller.handleAdbConnected("127.0.0.1", 5555)
                controller.updateQuickConnected(true)
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("main-tab-Terminal").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("main-tab-Terminal").performTouchInput { click() }
            compose.onNodeWithTag("cover-terminal-tools").assertIsDisplayed()
            assertTrue(compose.onNodeWithTag("terminal-output").fetchSemanticsNode().boundsInRoot.height > 0)
            capture("terminal-controls")
            compose.waitUntil(5_000) { compose.onAllNodesWithTag("main-tab-Files").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("main-tab-Files").performTouchInput { click() }
            compose.onNodeWithContentDescription(label(R.string.fm_cd_parent)).assertIsDisplayed()
            compose.onNodeWithContentDescription(label(R.string.fm_cd_sort)).assertIsDisplayed()
            capture("files-controls")
            compose.onNodeWithTag("file-actions").performTouchInput { click() }
            compose.onNodeWithText(label(R.string.fm_goto_path)).assertIsDisplayed().performTouchInput { click() }
            compose.onNodeWithText(label(R.string.button_confirm)).assertIsDisplayed()
            capture("file-path-form")
            compose.onNodeWithText(label(R.string.button_cancel)).performTouchInput { click() }
            compose.onNodeWithTag("main-tab-Settings").performTouchInput { click() }
            capture("settings-controls")
            compose.onNodeWithTag("main-tab-Devices").performTouchInput { click() }
            waitEnabled("cover-start")
            compose.onNodeWithTag("cover-start").assertIsDisplayed()
        } finally {
            runBlocking { withContext(Dispatchers.Main) { controller.disconnectAdbConnection() } }
        }
    }

    @Test(timeout = 60_000) fun wideSessionUsesRailAndExitsWithoutStopping() {
        val runtime = AppRuntime.obtainSession(Scrcpy.SessionConfig())
        val controller = runtime.services.connectionController
        val originalOptions = Storage.scrcpyOptions.bundleState.value
        try {
            runBlocking(Dispatchers.IO) {
                Storage.scrcpyOptions.saveBundle(originalOptions.copy(newDisplay = "1920x1080/240", startApp = "com.android.settings", audio = false, fullscreen = false, maxFps = "15", clipboardAutosync = false))
                controller.connectWithTimeout("127.0.0.1", 5555, 10_000)
                controller.handleAdbConnected("127.0.0.1", 5555)
                controller.updateQuickConnected(true)
            }
            waitEnabled("cover-start")
            compose.onNodeWithTag("cover-start").performTouchInput { click() }
            compose.waitUntil(20_000) { runtime.scrcpy.currentSessionState.value?.width == 1920 }
            val id = runtime.scrcpy.currentSessionState.value!!.controlSessionId
            waitEnabled("cover-open-fullscreen")
            compose.onNodeWithTag("cover-open-fullscreen").performTouchInput { click() }
            compose.onNodeWithTag("cover-control-rail").assertIsDisplayed()
            capture("wide-native-rail")
            val image = compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot
            assertEquals(1920f / 1080f, image.width / image.height, .01f)
            compose.onNodeWithContentDescription(label(R.string.cover_tools)).performTouchInput { click() }
            capture("wide-native-tools")
            compose.onNodeWithText(label(R.string.cover_mouse_pad)).performTouchInput { click() }
            compose.onNodeWithTag("cover-trackpad").assertIsDisplayed().performTouchInput { swipeUp() }
            capture("wide-native-pad")
            assertEquals(image, compose.onNodeWithTag("cover-image").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithContentDescription(label(R.string.cover_close_panel)).performTouchInput { click() }
            compose.onNodeWithContentDescription(label(R.string.cover_tools)).performTouchInput { click() }
            compose.onNodeWithText(label(R.string.cover_details)).performTouchInput { click() }
            capture("wide-native-details")
            compose.onNodeWithContentDescription(label(R.string.cover_close_panel)).performTouchInput { click() }
            compose.onNodeWithContentDescription(label(R.string.cover_exit)).performTouchInput { click() }
            compose.onNodeWithTag("cover-control").assertDoesNotExist()
            assertEquals(id, runtime.scrcpy.currentSessionState.value?.controlSessionId)
            compose.onNodeWithTag("cover-open-fullscreen").assertIsDisplayed()
        } finally {
            runBlocking { withContext(Dispatchers.Main) { controller.disconnectAdbConnection() } }
            runBlocking { Storage.scrcpyOptions.saveBundle(originalOptions) }
        }
    }

    private fun label(id: Int) = compose.activity.getString(id)

    private fun waitEnabled(tag: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().singleOrNull()?.config?.contains(SemanticsProperties.Disabled) == false
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Native overlay rendering has a separate animation clock from Compose tests.
        android.os.SystemClock.sleep(350)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "cover-validation").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
