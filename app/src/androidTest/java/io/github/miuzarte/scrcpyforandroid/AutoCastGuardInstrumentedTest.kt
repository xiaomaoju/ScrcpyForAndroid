package io.github.miuzarte.scrcpyforandroid

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastPolicy
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastShell
import io.github.miuzarte.scrcpyforandroid.autocast.Flip5FoldLease
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Real ADB shell lifetime tests, opt-in only on a disposable emulator. No Flip state is simulated. */
@RunWith(AndroidJUnit4::class)
class AutoCastGuardInstrumentedTest {
    private var connected = false
    private var stateId = 0

    @Before fun connectEmulator() = runBlocking(Dispatchers.IO) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("autoCastGuard") == "true")
        assumeTrue(Build.HARDWARE.contains("ranchu"))
        AppRuntime.init(ApplicationProvider.getApplicationContext<Context>())
        NativeAdbService.connect("127.0.0.1", 5555)
        connected = true
        val before = AutoCastPolicy.snapshot(AutoCastShell.execute("cmd device_state state"))
        assumeTrue(before.override == null)
        stateId = before.current.id
    }

    @After fun disconnect() = runBlocking(Dispatchers.IO) {
        if (connected) NativeAdbService.disconnect()
    }

    @Test(timeout = 30_000) fun closingAdbStreamRestoresState() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId)).use {
                assertEquals("READY", withTimeout(5_000) { runInterruptible { it.inputStream.bufferedReader().readLine() } })
                assertEquals(stateId, snapshot().override?.id)
            }
            awaitRestored()
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 20_000) fun releasingWithoutAnOwnedLeasePreservesExternalOverride() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("external-fold-lease-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            AutoCastShell.execute("cmd device_state state $stateId")
            val lease = Flip5FoldLease(preferences)
            lease.recover()
            lease.release()
            assertEquals(stateId, snapshot().override?.id)
        } finally {
            AutoCastShell.execute("cmd device_state state reset")
            preferences.edit().clear().commit()
        }
    }

    @Test(timeout = 20_000) fun endingACastResetsAnAdoptedExternalModeAndForgetsOldRestoreTargets() = runBlocking(Dispatchers.IO) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("adopted-fold-lease-test", Context.MODE_PRIVATE)
        preferences.edit().clear().putString("fold_previous", stateId.toString()).commit()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val lease = Flip5FoldLease(preferences)
        try {
            AutoCastShell.execute("cmd device_state state $stateId")
            // The emulator has one state: exercise the intermediate-mode protocol with it,
            // without claiming to simulate Samsung's TENT display/power behavior.
            lease.hold(scope, stateId, stateId, requestState = false, coverState = stateId)
            lease.markRunning()
            assertEquals(stateId, snapshot().override?.id)
            lease.release()
            assertNull(snapshot().override)
            assertFalse(preferences.contains("fold_token"))
            assertFalse(preferences.contains("fold_previous"))
            assertFalse(preferences.contains("fold_base"))
            assertFalse(preferences.contains("fold_cover"))
            lease.release() // Repeated stop/disconnect remains harmless.
            assertNull(snapshot().override)
        } finally {
            scope.cancel()
            lease.release()
            AutoCastShell.execute("cmd device_state state reset")
            preferences.edit().clear().commit()
        }
    }

    @Test(timeout = 20_000) fun recoveryCompletesAnInterruptedIntermediateModeReset() = runBlocking(Dispatchers.IO) {
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("intermediate-fold-recovery-test", Context.MODE_PRIVATE)
        val token = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId)).use { stream ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readLine() } })
                // The current override represents the intermediate step, not the original target.
                preferences.edit().clear().putString("fold_token", token).putInt("fold_target", stateId + 1)
                    .putInt("fold_base", stateId).putInt("fold_cover", stateId).commit()
                Flip5FoldLease(preferences).recover()
                assertNull(snapshot().override)
                assertFalse(preferences.contains("fold_token"))
            }
        } finally {
            AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId))
            preferences.edit().clear().commit()
        }
    }

    @Test(timeout = 20_000) fun adoptedExternalModeIsResetWhenItsTransportCloses() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            AutoCastShell.execute("cmd device_state state $stateId")
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId, requestState = false)).use { stream ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readLine() } })
                assertEquals(stateId, snapshot().override?.id)
            }
            awaitRestored()
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 20_000) fun failedStartupAlsoResetsAnAdoptedExternalMode() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            AutoCastShell.execute("cmd device_state state $stateId")
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId, startupSeconds = 2, requestState = false)).use { stream ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readLine() } })
                withTimeout(5_000) { awaitRestored() }
            }
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 20_000) fun adoptionDoesNotActivateAnAbsentExternalOverride() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId, requestState = false)).use { stream ->
                val output = withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readText() } }
                assertFalse(output.contains("READY"))
            }
            assertNull(snapshot().override)
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 30_000) fun missingHeartbeatRestoresStateWhileConnectionRemainsOpen() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId)).use {
                assertEquals("READY", withTimeout(5_000) { runInterruptible { it.inputStream.bufferedReader().readLine() } })
                assertEquals(stateId, snapshot().override?.id)
                awaitRestored()
            }
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 30_000) fun oldLeaseCannotRestoreOverNewLease() = runBlocking(Dispatchers.IO) {
        val oldToken = UUID.randomUUID().toString()
        val newToken = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(oldToken, stateId)).use { old ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { old.inputStream.bufferedReader().readLine() } })
                NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(newToken, stateId)).use { latest ->
                    assertEquals("READY", withTimeout(5_000) { runInterruptible { latest.inputStream.bufferedReader().readLine() } })
                    old.outputStream.write("STOP\n".toByteArray())
                    withTimeout(5_000) { runInterruptible { old.inputStream.readBytes() } }
                    assertEquals(stateId, snapshot().override?.id)
                    latest.outputStream.write("STOP\n".toByteArray())
                    awaitRestored()
                }
            }
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(newToken, stateId)) }
    }

    @Test(timeout = 20_000) fun startupDeadlineRestoresEvenWhileAppKeepsSendingHeartbeats() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId, stateId, 3)).use { stream ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readLine() } })
                val heartbeats = launch {
                    while (isActive && !stream.closed) {
                        runCatching { stream.outputStream.write("PING\n".toByteArray()) }
                        delay(300)
                    }
                }
                try { withTimeout(6_000) { awaitRestored() } }
                finally { heartbeats.cancelAndJoin() }
            }
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 20_000) fun confirmedRunningSessionOutlivesStartupDeadline() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId, stateId, 3)).use { stream ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readLine() } })
                stream.outputStream.write("RUNNING\n".toByteArray())
                delay(4_500)
                assertEquals(stateId, snapshot().override?.id)
                stream.outputStream.write("STOP\n".toByteArray())
                awaitRestored()
            }
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    @Test(timeout = 20_000) fun mismatchedPhysicalBaseRestoresOverride() = runBlocking(Dispatchers.IO) {
        val token = UUID.randomUUID().toString()
        try {
            // Exercise the comparison against a different physical base; no Samsung state is simulated.
            NativeAdbService.openShellStream(Flip5FoldLease.guardCommand(token, stateId, stateId + 1, coverState = stateId)).use { stream ->
                assertEquals("READY", withTimeout(5_000) { runInterruptible { stream.inputStream.bufferedReader().readLine() } })
                withTimeout(6_000) { awaitRestored() }
            }
        } finally { AutoCastShell.execute(Flip5FoldLease.restoreCommand(token, stateId)) }
    }

    private suspend fun snapshot() = AutoCastPolicy.snapshot(AutoCastShell.execute("cmd device_state state"))
    private suspend fun awaitRestored() = withTimeout(15_000) {
        while (snapshot().override != null) delay(200)
    }
}
