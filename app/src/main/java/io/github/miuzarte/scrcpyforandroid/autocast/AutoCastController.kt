package io.github.miuzarte.scrcpyforandroid.autocast

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.MainActivity
import io.github.miuzarte.scrcpyforandroid.NativeCoreFacade
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import io.github.miuzarte.scrcpyforandroid.models.DeviceShortcuts
import io.github.miuzarte.scrcpyforandroid.scrcpy.Scrcpy
import io.github.miuzarte.scrcpyforandroid.scrcpy.Shared.VideoSource
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.services.AppScreenOn
import io.github.miuzarte.scrcpyforandroid.services.DeviceConnectionServices
import io.github.miuzarte.scrcpyforandroid.services.ScrcpyLaunchSettings
import io.github.miuzarte.scrcpyforandroid.storage.ScrcpyOptions
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-owned operation for the widget, launcher alias and setup page. */
internal class AutoCastController(
    private val context: Context,
    val scrcpy: Scrcpy,
    private val services: DeviceConnectionServices,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val preferences = context.getSharedPreferences("flip5_autocast", Context.MODE_PRIVATE)
    private val diagnostics = AutoCastDiagnostics { report -> preferences.edit().putString("startup_diagnostics", report).apply() }
    private val foldLease = Flip5FoldLease(preferences, diagnostics)
    private val mutableState = MutableStateFlow(AutoCastState())
    val state = mutableState.asStateFlow()
    @Volatile private var operation: Job? = null
    private var onCover = false
    var playbackDisplayId: Int = -1
    @Volatile private var ownsConnection = false
    @Volatile private var ownsStream = false
    private var launchSettings: ScrcpyLaunchSettings.Snapshot? = null
    private var startAppOverride: String? = null
    private var externalInnerRequest = false
    private var usingDualMode = false
    private var restoreFailure: String? = null
    private var framesBefore = 0L
    private var decodedBefore = 0L
    private var lastSystemDisplays = ""
    private val sizeListener: (Int, Int) -> Unit = { width, height -> scrcpy.updateCurrentSessionSize(width, height) }

    val supported: Boolean get() = AutoCastPolicy.supports(Build.MANUFACTURER, Build.MODEL)
    val active: Boolean get() = operation?.isActive == true || ownsConnection || ownsStream

    fun start(cover: Boolean, startApp: String? = null, externalInnerRequest: Boolean = false) {
        onCover = cover
        if (!supported) { update(AutoCastPhase.UNSUPPORTED); return }
        if (operation?.isActive == true || state.value.phase == AutoCastPhase.RUNNING) return
        startAppOverride = startApp
        this.externalInnerRequest = externalInnerRequest
        operation = scope.launch { connectAndRun() }
    }

    fun pair(input: String) {
        if (operation?.isActive == true || !supported) return
        val code = AutoCastPolicy.pairingCode(input)
        if (code == null) { update(AutoCastPhase.NEEDS_PAIRING, "请输入系统显示的六位配对码。"); return }
        operation = scope.launch {
            update(AutoCastPhase.PAIRING)
            try {
                NativeAdbService.keyName = Storage.appSettings.bundleState.value.adbKeyName
                val pairing = NativeAdbService.discoverPairingService(timeoutMs = 6_000, includeLanDevices = false)
                    ?: error("未发现本机配对窗口。请保持系统的“使用配对码配对设备”窗口打开，再从通知输入配对码。")
                // Only loopback is ever passed to the transport, including first-time pairing.
                withContext(Dispatchers.IO) { NativeAdbService.pair("127.0.0.1", pairing.second, code) }
                connectAndRun()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                update(AutoCastPhase.NEEDS_PAIRING, error.message.orEmpty().take(240))
            }
        }
    }

    fun stop() {
        if (state.value.phase == AutoCastPhase.IDLE && !active) return
        if (state.value.phase == AutoCastPhase.STOPPING) return
        val previous = operation
        update(AutoCastPhase.STOPPING)
        NativeAdbService.cancelPendingConnect()
        operation = scope.launch {
            previous?.cancelAndJoin()
            val cleanupError = cleanup()
            if (cleanupError == null) update(AutoCastPhase.IDLE)
            else update(AutoCastPhase.ERROR, "恢复屏幕状态失败，请重新连接后重试：$cleanupError")
        }
    }

    fun onWindowRemoved() {
        // Samsung may remove the old task while dual mode is being applied. The foreground
        // operation must survive long enough to launch its cover window after that transition.
        // Explicit Back/End still calls stop(); the startup guard bounds this exception.
        if (state.value.phase != AutoCastPhase.PREPARING) stop()
    }

    suspend fun stopAndAwait() {
        stop()
        operation?.join()
    }

    private suspend fun connectAndRun() {
        diagnostics.begin("version=${BuildConfig.VERSION_NAME}; model=${Build.MODEL}; sdk=${Build.VERSION.SDK_INT}; os=${Build.VERSION.RELEASE}; build=${Build.DISPLAY}; time=${java.time.Instant.now()}")
        lastSystemDisplays = ""
        try {
            update(AutoCastPhase.CONNECTING)
            diagnostics.enter(AutoCastStep.P1)
            val shortcuts = DeviceShortcuts.unmarshalFrom(Storage.quickDevices.loadBundle().quickDevicesList)
            val profileId = ScrcpyLaunchSettings.profileId(AppRuntime.currentConnectionTarget, shortcuts, AppRuntime.currentConnectionProfileId.value)
            launchSettings = ScrcpyLaunchSettings.load(profileId).let { configured ->
                startAppOverride?.takeIf { it.isNotBlank() }?.let {
                    configured.copy(options = configured.options.copy(startApp = it))
                } ?: configured
            }
            scrcpy.sessionConfig = checkNotNull(launchSettings).session
            if (scrcpy.isStarted() && !ownsStream) {
                val target = AppRuntime.currentConnectionTarget
                val local = AutoCastIntents.isLocalTarget(target)
                check(local) { "已有其他设备的投屏正在运行。请先结束它，再使用打开内屏。" }
                // A previous manual self-cast may already contain the cover player. Rebuild it after preparing dual mode.
                scrcpy.stop()
            }
            if (!connectLocal()) { update(AutoCastPhase.NEEDS_PAIRING); return }
            // Restore interrupted setup before another override can be applied.
            diagnostics.enter(AutoCastStep.R1)
            foldLease.recover()
            restoreFailure = null
            if (!onCover) { update(AutoCastPhase.READY); return }
            var recovered = false
            var retryInnerMode = false
            while (currentCoroutineContext().isActive) {
                try {
                    startInnerScreen(retryInnerMode)
                    monitor()
                    update(AutoCastPhase.STOPPING)
                    val cleanupError = cleanup()
                    check(cleanupError == null) { cleanupError.orEmpty() }
                    update(AutoCastPhase.IDLE)
                    return
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // One bounded automatic retry. Setup/unsupported-state errors remain actionable.
                    if (recovered || state.value.phase != AutoCastPhase.RUNNING) throw error
                    recordFailure(error)
                    retryInnerMode = usingDualMode
                    cleanup()
                    recovered = true
                    update(AutoCastPhase.RECOVERING)
                    if (!connectLocal()) { update(AutoCastPhase.NEEDS_PAIRING); return }
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            val message = recordFailure(timeout)
            val cleanupError = cleanup()
            update(AutoCastPhase.ERROR, listOfNotNull(message, cleanupError).joinToString("\n"))
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { cleanup() }
            throw cancelled
        } catch (error: Exception) {
            val message = recordFailure(error)
            val cleanupError = cleanup()
            update(AutoCastPhase.ERROR, listOfNotNull(message, cleanupError).joinToString("\n").take(350))
        }
    }

    private suspend fun connectLocal(): Boolean {
        diagnostics.enter(AutoCastStep.C1)
        NativeAdbService.keyName = Storage.appSettings.bundleState.value.adbKeyName
        val current = AppRuntime.currentConnectionTarget
        if (current != null && AutoCastIntents.isLocalTarget(current) && NativeAdbService.isConnected()) {
            ownsConnection = true
            try {
                AutoCastShell.execute(":")
                preferences.edit().putInt("port", current.port).apply()
                return true
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
        services.connectionController.disconnectAdbConnection(stopAutoCast = false)
        val candidates = listOf(preferences.getInt("port", 0), 5555).filter { it in 1..65535 }.distinct()
        for (port in candidates) if (tryPort(port)) return true
        val discovered = NativeAdbService.discoverConnectService(timeoutMs = 6_000, includeLanDevices = false)
        return discovered != null && tryPort(discovered.second)
    }

    private suspend fun tryPort(port: Int): Boolean {
        try {
            services.adbCoordinator.connectWithTimeout("127.0.0.1", port, 3_000)
            ownsConnection = true
            services.connectionController.handleAdbConnected("127.0.0.1", port, launchSettings?.profileId ?: ScrcpyOptions.GLOBAL_PROFILE_ID)
            AutoCastShell.execute(":")
            preferences.edit().putInt("port", port).apply()
            return true
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            services.connectionController.disconnectAdbConnection(stopAutoCast = false)
            ownsConnection = false
            return false
        }
    }

    private suspend fun startInnerScreen(retryInnerMode: Boolean) {
        // Closing the transport also unblocks scrcpy's blocking handshake reads. The shell
        // independently restores the fold state even if this process can no longer schedule work.
        coroutineScope {
            val watchdog = launch {
                delay(25_000)
                diagnostics.note("watchdog", "25-second startup deadline expired at ${diagnostics.step.name}")
                Log.e("Flip5AutoCast", "Startup deadline expired; restoring fold state and closing ADB")
                runCatching { foldLease.release() }
                NativeAdbService.disconnect()
            }
            try { prepareInnerScreen(retryInnerMode) }
            catch (error: Exception) {
                if (error is CancellationException && error !is TimeoutCancellationException) throw error
                // Capture while the lease is still held. The startup watchdog remains active;
                // diagnostics must never postpone recovery or replace the original failure.
                if (diagnostics.step in setOf(AutoCastStep.W1, AutoCastStep.W2, AutoCastStep.D1)) {
                    recordDisplayFailure()
                }
                throw error
            }
            finally { watchdog.cancelAndJoin() }
        }
    }

    private suspend fun prepareInnerScreen(retryInnerMode: Boolean) {
        update(AutoCastPhase.PREPARING)
        AppScreenOn.acquire()
        val configured = checkNotNull(launchSettings)
        // Cleanup now resets adopted external modes too. A bounded reconnect must recreate
        // that inner mode rather than accidentally falling back to direct cover capture.
        val allowActivation = AutoCastPolicy.shouldPrepareInner(configured.activateFlipInner, externalInnerRequest) || retryInnerMode
        val options = configured.options
        val innerConfiguration = options.video && options.videoPlayback && options.videoSource == VideoSource.DISPLAY &&
            options.newDisplay.isBlank() && options.displayId in -1..Display.DEFAULT_DISPLAY
        // Adopt an externally prepared mode even when configuration validation will fail,
        // so every failed inner-screen attempt can shut it down. Invalid options never activate it.
        diagnostics.note("before fold activation", displaySnapshot())
        usingDualMode = foldLease.acquire(scope, allowActivation = allowActivation && innerConfiguration)
        if (allowActivation || usingDualMode) {
            diagnostics.enter(AutoCastStep.P1)
            check(innerConfiguration) {
                "主界面当前配置不适用于内屏投屏，请选择屏幕 0（或默认屏幕）并启用视频显示。"
            }
        }
        diagnostics.note("activation", "enabled=${configured.activateFlipInner}; externalRequest=$externalInnerRequest; dualMode=$usingDualMode")
        if (!usingDualMode) {
            startConfiguredVideo()
            return
        }
        // Samsung can suspend/reparent the already-open window during the state transition.
        // Match the working launcher order: dual mode first, then launch on the cover.
        diagnostics.enter(AutoCastStep.W1)
        val destinationId = awaitCover("双屏切换后等待 5 秒仍未找到外屏。") {
            AutoCastIntents.coverDisplay(context)?.displayId
                ?: AutoCastSystemDisplays.cover(readSystemDisplays())?.id
        }
        diagnostics.note("cover destination", "id=$destinationId; ${displaySnapshot()}")
        diagnostics.enter(AutoCastStep.W2)
        if (playbackDisplayId != destinationId) {
            val launch = AutoCastShell.execute("am start -W --display $destinationId " +
            "-n ${context.packageName}/${MainActivity::class.java.name} -a ${AutoCastIntents.ACTION_OPEN} " +
            "-f 0x34000000")
            diagnostics.note("cover launch result", launch)
            check(!launch.contains("Error:", ignoreCase = true) && !launch.contains("Exception")) {
                "无法在外屏恢复投屏窗口：${launch.take(160)}"
            }
        } else diagnostics.note("cover launch result", "window already on cover $destinationId")
        diagnostics.enter(AutoCastStep.D1)
        diagnostics.note("required displays", "source=default display 0 with current positive dimensions; recognized non-default cover ON(${Display.STATE_ON}); window must be on cover")
        awaitCover("等待 5 秒后外屏窗口仍未就绪，请复制诊断查看屏幕状态和窗口位置。") {
            if (coverWindowReady(destinationId)) Unit else null
        }
        diagnostics.note("main settings", "profile=${configured.profileId}; customServer=${configured.session.customServerUri != null}; " +
            "version=${configured.session.serverVersion}; lowLatency=${configured.session.lowLatency}; " +
            "display=${options.displayId}; maxSize=${options.maxSize}; maxFps=${options.maxFps}; " +
            "bitRate=${options.videoBitRate}; codec=${options.videoCodec}; audio=${options.audio}; " +
            "clipboard=${options.clipboardAutosync}; control=${options.control}; powerOn=${options.powerOn}")
        diagnostics.enter(AutoCastStep.D2)
        val sources = scrcpy.listings.getDisplays(forceRefresh = true)
        diagnostics.note("capture sources", sources.joinToString { "${it.id}:${it.width}x${it.height}" })
        val inner = sources.singleOrNull { it.id == Display.DEFAULT_DISPLAY }
        check(inner != null && AutoCastPolicy.acceptsCapture(inner.id, playbackDisplayId, inner.width, inner.height)) {
            "内屏捕获源未就绪，已阻止投屏到外屏。"
        }
        // Reopening the window can change Samsung's device state. Source 0 is only the inner
        // source while the named inner-default dual mode remains committed; size cannot prove it.
        val foldBeforeVideo = AutoCastShell.execute("cmd device_state state")
        diagnostics.note("fold before capture", foldBeforeVideo)
        check(AutoCastPolicy.innerDefaultActive(AutoCastPolicy.snapshot(foldBeforeVideo), foldLease.targetId)) {
            "双屏模式在启动画面前已退出，已停止本次投屏。"
        }
        Log.i("Flip5AutoCast", "validated fold=${foldLease.targetId}, source=${inner.id} ${inner.width}x${inner.height}, window=$playbackDisplayId")
        startConfiguredVideo()
    }

    private suspend fun startConfiguredVideo() {
        val options = checkNotNull(launchSettings).options
        update(AutoCastPhase.STARTING)
        NativeCoreFacade.addVideoSizeListener(sizeListener)
        framesBefore = NativeCoreFacade.renderedVideoFrames
        decodedBefore = NativeCoreFacade.decodedVideoFrames
        ownsStream = true
        diagnostics.enter(AutoCastStep.V1)
        scrcpy.start(options)
        if (options.startApp.isNotBlank() && options.control) {
            runCatching { scrcpy.startApp(options.startApp) }.onFailure {
                Log.w("Flip5AutoCast", "Configured start-app failed", it)
            }
        }
        diagnostics.enter(AutoCastStep.V2)
        if (options.video && options.videoPlayback) withTimeout(10_000) {
            while (NativeCoreFacade.renderedVideoFrames <= framesBefore || NativeCoreFacade.decodedVideoFrames <= decodedBefore) delay(100)
        }
        services.connectionController.markScrcpyStarted()
        if (!options.disableScreensaver) AppScreenOn.release()
        foldLease.markRunning()
        update(AutoCastPhase.RUNNING)
    }

    private suspend fun <T : Any> awaitCover(timeoutMessage: String, findReady: suspend () -> T?): T {
        var samples = 0
        var lastFold = "not sampled"
        var lastDisplays = ""
        return try {
            AutoCastDisplayWaiter.await(foldLease.targetId, timeoutMessage, readFold = {
                lastFold = AutoCastShell.execute("cmd device_state state")
                AutoCastPolicy.snapshot(lastFold)
            }, findReady = {
                samples++
                val snapshot = displaySnapshot()
                // Retain transitions, not every identical poll, in the bounded remote report.
                if (snapshot != lastDisplays) diagnostics.note("display transition", snapshot)
                lastDisplays = snapshot
                findReady()
            })
        } finally {
            diagnostics.note("display wait result", "stage=${diagnostics.step}; samples=$samples; $lastDisplays")
            diagnostics.note("fold at display wait", lastFold)
        }
    }

    private suspend fun readSystemDisplays(): List<AutoCastSystemDisplays.LogicalDisplay> {
        val displays = AutoCastSystemDisplays.parse(AutoCastShell.execute("dumpsys display"))
        val summary = AutoCastSystemDisplays.summary(displays)
        if (summary != lastSystemDisplays) diagnostics.note("shell logical displays (app list incomplete)", summary)
        lastSystemDisplays = summary
        return displays
    }

    private suspend fun coverWindowReady(destinationId: Int): Boolean {
        val displays = context.getSystemService(DisplayManager::class.java)
        val inner = displays.getDisplay(Display.DEFAULT_DISPLAY)
        val cover = AutoCastIntents.coverDisplay(context)
        if (inner != null && cover != null) {
            return cover.displayId == destinationId && AutoCastPolicy.displaysReady(
                inner.displayId, inner.mode.physicalWidth, inner.mode.physicalHeight,
                cover.displayId, cover.state == Display.STATE_ON, playbackDisplayId)
        }
        // A fresh shell snapshot is needed for both startup and monitoring; never treat the
        // initially discovered ID or am start's success as proof that the window is on the cover.
        return AutoCastSystemDisplays.windowReady(readSystemDisplays(), destinationId, playbackDisplayId)
    }

    private suspend fun recordDisplayFailure() {
        diagnostics.note("failure displays before recovery", displaySnapshot())
        try {
            val completed = withTimeoutOrNull(1_500) {
                diagnostics.note("fold at display failure", AutoCastShell.execute("cmd device_state state"))
                val dump = AutoCastShell.execute("dumpsys display")
                diagnostics.note("system displays", AutoCastDiagnostics.displayDumpSummary(dump))
                diagnostics.note("system logical status", AutoCastSystemDisplays.summary(AutoCastSystemDisplays.parse(dump)))
                true
            }
            if (completed == null) diagnostics.note("system displays", "diagnostic deadline reached (1500ms)")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnostics.note("system displays", "unavailable: ${error.javaClass.simpleName}")
        }
    }

    private suspend fun monitor() {
        diagnostics.enter(AutoCastStep.M1)
        while (currentCoroutineContext().isActive) {
            delay(2_000)
            check(scrcpy.isStarted()) { "投屏已断开，正在重新连接。" }
            if (usingDualMode) {
                val fold = AutoCastPolicy.snapshot(AutoCastShell.execute("cmd device_state state"))
                if (!AutoCastPolicy.innerDefaultActive(fold, foldLease.targetId) ||
                    !coverWindowReady(playbackDisplayId)) return
            }
        }
    }

    private suspend fun cleanup(): String? = withContext(NonCancellable + Dispatchers.Main) {
        // Restore the phone before waiting for decoder/socket teardown, which may be blocked.
        if (ownsConnection) restoreFailure = runCatching { withContext(Dispatchers.IO) { foldLease.release() } }.exceptionOrNull()?.let {
            it.message ?: it.javaClass.simpleName
        }
        if (ownsStream) {
            runCatching { scrcpy.stop() }
            ownsStream = false
        }
        NativeCoreFacade.removeVideoSizeListener(sizeListener)
        if (ownsConnection) AppScreenOn.release()
        if (ownsConnection) {
            runCatching { services.connectionController.disconnectAdbConnection(stopAutoCast = false) }
            ownsConnection = false
        }
        restoreFailure
    }

    private fun update(phase: AutoCastPhase, message: String = "") {
        Log.i("Flip5AutoCast", "phase=$phase window=$playbackDisplayId $message")
        mutableState.value = AutoCastState(phase, message, preferences.getString("last_startup_failure", "").orEmpty())
    }

    private fun recordFailure(error: Exception): String {
        // Capture before cleanup changes the fold state or tears down the decoder.
        diagnostics.note("failure displays", displaySnapshot())
        diagnostics.note("video frames", "decoded=$decodedBefore->${NativeCoreFacade.decodedVideoFrames}; rendered=$framesBefore->${NativeCoreFacade.renderedVideoFrames}; started=${scrcpy.isStarted()}")
        val message = diagnostics.failure(error, error is TimeoutCancellationException)
        preferences.edit().putString("last_startup_failure", diagnostics.report()).apply()
        return message
    }

    private fun displaySnapshot(): String = runCatching {
        "window=$playbackDisplayId; " + context.getSystemService(DisplayManager::class.java).displays.joinToString("; ") { display ->
            "id=${display.displayId}, state=${display.state}, " +
                "mode=${display.mode.physicalWidth}x${display.mode.physicalHeight}, " +
                "supported=${display.supportedModes.map { "${it.physicalWidth}x${it.physicalHeight}" }.distinct()}"
        }
    }.getOrElse { "display read failed: ${it.javaClass.simpleName}" }

}
