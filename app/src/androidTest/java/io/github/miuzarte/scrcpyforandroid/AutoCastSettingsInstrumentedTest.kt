package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastIntents
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcut
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.ScrcpyLaunchSettings
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.AppSettings
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoCastSettingsInstrumentedTest {
    @Before fun initialize() { AppRuntime.init(ApplicationProvider.getApplicationContext<Context>()) }

    @Test fun activationNeverTreatsUsbOrRemoteTargetsAsThisPhone() = runBlocking {
        assertTrue(AutoCastIntents.isLocalTarget(ConnectionTarget("127.0.0.1", 5555)))
        assertTrue(AutoCastIntents.isLocalTarget(ConnectionTarget("::1", 5555)))
        assertFalse(AutoCastIntents.isLocalTarget(null))
        assertFalse(AutoCastIntents.isLocalTarget(ConnectionTarget("192.0.2.1", 5555)))
        assertFalse(AutoCastIntents.isLocalTarget(ConnectionTarget("127.0.0.1", 0)))
        assertFalse(AutoCastIntents.isLocalTarget(ConnectionTarget("127.0.0.1", 5555, connectionType = DeviceConnectionType.USB)))
    }

    @Test fun activationDefaultsOnAndBothEntriesReadPersistedChanges() = runBlocking {
        assertTrue(AppSettings.ACTIVATE_FLIP_INNER.defaultValue)
        val original = Storage.appSettings.loadBundle()
        try {
            for (enabled in listOf(false, true)) {
                Storage.appSettings.saveBundle(original.copy(activateFlipInner = enabled))
                assertEquals(enabled, Storage.appSettings.loadBundle().activateFlipInner)
                assertEquals(enabled, ScrcpyLaunchSettings.load(ScrcpyOptions.GLOBAL_PROFILE_ID).activateFlipInner)
            }
        } finally { Storage.appSettings.saveBundle(original) }
    }

    @Test fun loadsMainGlobalOptionsAndServerSettingsWithoutShortcutOverrides() = runBlocking {
        val original = Storage.scrcpyOptions.loadBundle()
        val originalApp = Storage.appSettings.loadBundle()
        val configured = original.copy(maxSize = 0, videoBitRate = 12_000_000, maxFps = "48",
            audio = true, clipboardAutosync = true, powerOn = true, gamepad = true)
        val configuredApp = originalApp.copy(customServerUri = "content://diagnostic/server.jar",
            customServerVersion = "4.1", serverRemotePath = "/data/local/tmp/config-test.jar", lowLatency = true)
        try {
            Storage.scrcpyOptions.saveBundle(configured)
            Storage.appSettings.saveBundle(configuredApp)
            withTimeout(5_000) {
                Storage.scrcpyOptions.bundleState.first { it == configured }
                Storage.appSettings.bundleState.first { it == configuredApp }
            }
            val loaded = ScrcpyLaunchSettings.load(ScrcpyOptions.GLOBAL_PROFILE_ID)
            assertEquals(Storage.scrcpyOptions.toClientOptions(configured).fix(), loaded.options)
            assertEquals(ScrcpyLaunchSettings.session(configuredApp), loaded.session)
            assertEquals(0, loaded.options.maxSize.toInt())
            assertEquals(12_000_000, loaded.options.videoBitRate)
            assertEquals("48", loaded.options.maxFps)
            assertTrue(loaded.options.audio && loaded.options.clipboardAutosync && loaded.options.powerOn)
        } finally {
            Storage.scrcpyOptions.saveBundle(original)
            Storage.appSettings.saveBundle(originalApp)
        }
    }

    @Test fun readsSelectedPresetFreshAndPreservesItsExplicitSizeLimit() = runBlocking {
        val originalProfiles = Storage.scrcpyProfiles.loadState()
        val global = Storage.scrcpyOptions.loadBundle()
        try {
            val preset = Storage.scrcpyProfiles.createProfile("AutoCast configuration test",
                global.copy(maxSize = 1440, videoBitRate = 9_000_000, maxFps = "55", audio = false))
            val first = ScrcpyLaunchSettings.load(preset.id)
            assertEquals(Storage.scrcpyOptions.toClientOptions(preset.bundle).fix(), first.options)
            assertEquals(1440, first.options.maxSize.toInt())
            Storage.scrcpyProfiles.updateBundle(preset.id, preset.bundle.copy(maxSize = 0, maxFps = "72"))
            val next = ScrcpyLaunchSettings.load(preset.id)
            assertEquals(0, next.options.maxSize.toInt())
            assertEquals("72", next.options.maxFps)
            assertEquals(global, Storage.scrcpyOptions.loadBundle())
            assertEquals(Storage.scrcpyOptions.toClientOptions(global).fix(), ScrcpyLaunchSettings.load("deleted-profile").options)
        } finally { Storage.scrcpyProfiles.saveState(originalProfiles) }
    }

    @Test fun profileSelectionMatchesMainScreenBeforeConnectionReset() {
        val target = ConnectionTarget("127.0.0.1", 5555)
        val shortcuts = DeviceShortcuts(listOf(DeviceShortcut(addresses = listOf("127.0.0.1:5555"), scrcpyProfileId = "device-profile")))
        assertEquals("device-profile", ScrcpyLaunchSettings.profileId(target, shortcuts, "selected-profile"))
        assertEquals("selected-profile", ScrcpyLaunchSettings.profileId(null, shortcuts, "selected-profile"))
        assertEquals("selected-profile", ScrcpyLaunchSettings.profileId(target, DeviceShortcuts(emptyList()), "selected-profile"))
    }
}
