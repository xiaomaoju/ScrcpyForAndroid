package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastDiagnostics
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastDisplayWaiter
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastIntents
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastPolicy
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastSystemDisplays
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCastDisplayWaitInstrumentedTest {
    @Test fun delayedDisplayIsDiscoveredThroughAndroidDisplayManager() = runBlocking {
        assumeTrue(Build.HARDWARE.contains("ranchu"))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val displays = context.getSystemService(DisplayManager::class.java)
        // Dedicated emulator only: a test-owned display is not evidence of Samsung folding.
        assertNull("Run on a dedicated emulator without a secondary cover", AutoCastIntents.coverDisplay(context))
        val closed = AutoCastPolicy.FoldState(0, "CLOSED")
        val dual = AutoCastPolicy.FoldState(4, "CONCURRENT_INNER_DEFAULT")
        val fold = AutoCastPolicy.FoldSnapshot(dual, closed, dual)
        var virtual: VirtualDisplay? = null
        val appearance = launch {
            delay(1_200)
            virtual = displays.createVirtualDisplay("AutoCast wait test", 720, 748, 160, null, 0)
            assertNotNull(virtual)
        }
        try {
            val cover = AutoCastDisplayWaiter.await(4, "test cover missing", { fold }, { AutoCastIntents.coverDisplay(context) })
            assertEquals(virtual!!.display.displayId, cover.displayId)
            val shell = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys display")
            val dump = ParcelFileDescriptor.AutoCloseInputStream(shell).bufferedReader().use { it.readText() }
            val summary = AutoCastDiagnostics.displayDumpSummary(dump)
            assertTrue(summary.contains("displayId ${cover.displayId}"))
            assertTrue(summary.contains("720 x 748"))
            assertFalse(summary.contains("AutoCast wait test"))
            assertFalse(summary.contains("uniqueId"))
            val logical = AutoCastSystemDisplays.parse(dump)
            assertTrue(logical.any { it.id == 0 && it.type == "INTERNAL" })
            assertTrue(logical.any { it.id == cover.displayId && it.width == 720 && it.height == 748 && it.type == "VIRTUAL" })
            assertNull("Shell fallback must not select a virtual test display as a physical cover", AutoCastSystemDisplays.cover(logical))
        } finally {
            appearance.cancel()
            virtual?.release()
        }
    }
}
