package io.github.miuzarte.scrcpyforandroid.autocast

import android.content.SharedPreferences
import io.github.miuzarte.scrcpyforandroid.nativecore.AdbSocketStream
import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.util.UUID

/** Every dual-screen cast owns its shutdown, including a mode prepared by an external launcher. */
internal class Flip5FoldLease(private val preferences: SharedPreferences, private val diagnostics: AutoCastDiagnostics? = null) {
    private var stream: AdbSocketStream? = null
    private var reader: BufferedReader? = null
    private var heartbeat: Job? = null
    @Volatile private var running = false
    var targetId: Int = -1
        private set

    suspend fun acquire(scope: CoroutineScope, allowActivation: Boolean = true): Boolean = withContext(Dispatchers.IO) {
        diagnostics?.enter(AutoCastStep.R1)
        recover()
        diagnostics?.enter(AutoCastStep.F1)
        val supportedOutput = AutoCastShell.execute("cmd device_state print-states")
        diagnostics?.note("supported fold states", supportedOutput)
        val supported = AutoCastPolicy.states(supportedOutput)
        val target = AutoCastPolicy.dualState(supported)
        if (target == null && !allowActivation) return@withContext false
        requireNotNull(target) { "未找到可用的双屏模式，请保留此提示供真机适配。" }
        targetId = target.id
        diagnostics?.enter(AutoCastStep.F2)
        val beforeOutput = AutoCastShell.execute("cmd device_state state")
        diagnostics?.note("initial fold state", beforeOutput)
        val before = AutoCastPolicy.snapshot(beforeOutput)
        val alreadyPrepared = AutoCastPolicy.innerDefaultActive(before, target.id)
        if (!alreadyPrepared && !allowActivation) return@withContext false
        check(AutoCastPolicy.isClosed(before.base)) { "请合上手机，再从外屏点击打开内屏。" }
        diagnostics?.note("fold ownership", if (alreadyPrepared) "adopting external dual mode; reset on exit" else "activating dual mode; reset on exit")
        hold(scope, target.id, before.base.id, requestState = !alreadyPrepared,
            coverState = AutoCastPolicy.coverOnlyState(supported)?.id)
        true
    }

    /** Enroll both newly requested and already active modes in the same crash-safe cleanup. */
    internal suspend fun hold(scope: CoroutineScope, target: Int, base: Int, requestState: Boolean,
        coverState: Int? = null) = withContext(Dispatchers.IO) {
        targetId = target
        val token = UUID.randomUUID().toString()
        // Never reinstate another artificial fold mode after the cast has ended.
        check(preferences.edit().putString("fold_token", token).putInt("fold_target", target)
            .putInt("fold_base", base).putInt("fold_cover", coverState ?: -1)
            .remove("fold_previous").commit())
        running = false
        diagnostics?.enter(AutoCastStep.F3)
        val opened = NativeAdbService.openShellStream(guardCommand(token, target, base, requestState = requestState, coverState = coverState))
        stream = opened
        reader = opened.inputStream.bufferedReader()
        val ready = withTimeout(6_000) { runInterruptible(Dispatchers.IO) { reader!!.readLine() } }
        diagnostics?.note("guard", "target=$target, base=$base, exitCover=$coverState, requestState=$requestState, reply=$ready")
        check(ready == "READY") { "双屏模式启动失败：${ready.orEmpty().take(160)}" }
        heartbeat = scope.launch(Dispatchers.IO) {
            while (isActive && !opened.closed) {
                delay(2_000)
                try { opened.outputStream.write(if (running) "RUNNING\n".toByteArray() else "PING\n".toByteArray()) }
                catch (_: Exception) { break }
            }
        }
        diagnostics?.enter(AutoCastStep.F4)
        var observed = "No fold-state response received"
        try { withTimeout(5_000) {
            while (true) {
                observed = AutoCastShell.execute("cmd device_state state")
                if (AutoCastPolicy.snapshot(observed).current.id == target) break
                delay(150)
            }
        } } finally { diagnostics?.note("committed fold state", observed) }
    }

    fun markRunning() { running = true }

    suspend fun release() {
        running = false
        heartbeat?.cancelAndJoin()
        heartbeat = null
        stream?.let { opened ->
            try {
                runCatching {
                    opened.outputStream.write("STOP\n".toByteArray())
                    withTimeout(4_000) { runInterruptible(Dispatchers.IO) { reader?.readText() } }
                }
            } finally {
                opened.close()
                stream = null
                reader = null
            }
        }
        recover()
    }

    /** Persisted token also covers process death between saving the lease and receiving READY. */
    suspend fun recover() = withContext(Dispatchers.IO) {
        val token = preferences.getString("fold_token", null) ?: return@withContext
        val target = preferences.getInt("fold_target", -1)
        val base = preferences.getInt("fold_base", -1).takeIf { it >= 0 }
        val cover = preferences.getInt("fold_cover", -1).takeIf { it >= 0 }
        check(token.matches(Regex("[a-f0-9-]{36}")) && target >= 0)
        AutoCastShell.execute(restoreCommand(token, target, base, cover))
        check(preferences.edit().remove("fold_token").remove("fold_target").remove("fold_previous")
            .remove("fold_base").remove("fold_cover").commit())
    }

    companion object {
        private const val LEASE_FILE = "/data/local/tmp/scrcpyforandroid-autocast-fold.lease"

        internal fun guardCommand(token: String, target: Int, base: Int? = null,
            startupSeconds: Int = 25, requestState: Boolean = true, coverState: Int? = null): String = """
            printf '%s' '$token' > $LEASE_FILE || exit 1
            chmod 600 $LEASE_FILE
            restore() {
                ${restoreCommand(token, target, base, coverState)}
            }
            trap restore EXIT
            trap 'exit' HUP TERM INT
            SECONDS=0
            ${if (requestState) "cmd device_state state $target || exit 1" else """
            # Adopt only a still-active override. Do not reapply the launcher's request.
            cmd device_state state | grep -Eq 'Override state:.*(identifier|mIdentifier)=$target,' || exit 1
            """.trimIndent()}
            printf 'READY\n'
            last_ping=0
            running=0
            while :; do
                # A live app is not proof that startup completed or that unfolding is safe.
                [ "${'$'}running" = 0 ] && [ "${'$'}SECONDS" -ge $startupSeconds ] && break
                [ "${'$'}((SECONDS - last_ping))" -ge 10 ] && break
                ${if (base == null) ":" else """
                fold_state=${'$'}(cmd device_state state) || break
                if printf '%s\n' "${'$'}fold_state" | grep -q 'Base state:'; then
                    printf '%s\n' "${'$'}fold_state" | grep -Eq 'Base state:.*(identifier|mIdentifier)=$base,' || break
                fi
                """.trimIndent()}
                IFS= read -r -t 1 signal
                read_status=${'$'}?
                [ "${'$'}read_status" = 1 ] && break
                if [ "${'$'}read_status" = 0 ]; then
                    [ "${'$'}signal" = STOP ] && break
                    last_ping=${'$'}SECONDS
                    [ "${'$'}signal" = RUNNING ] && running=1
                fi
            done
        """.trimIndent()

        internal fun restoreCommand(token: String, target: Int, base: Int? = null, coverState: Int? = null): String = """
            if [ "${'$'}(cat $LEASE_FILE 2>/dev/null)" = '$token' ]; then
                current=${'$'}(cmd device_state state) || exit 1
                ${if (base == null || coverState == null) ":" else """
                # Leave dual mode through cover-only mode without waking or sleeping the phone.
                # If the physical base changed, reset directly instead of forcing the cover.
                if printf '%s\n' "${'$'}current" | grep -Eq 'Override state:.*(identifier|mIdentifier)=$target,' &&
                    printf '%s\n' "${'$'}current" | grep -Eq 'Base state:.*(identifier|mIdentifier)=$base,'; then
                    cmd device_state state $coverState || exit 1
                    cover_ready=0
                    for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
                        current=${'$'}(cmd device_state state) || exit 1
                        if printf '%s\n' "${'$'}current" | grep -Eq 'Committed state:.*(identifier|mIdentifier)=$coverState,'; then
                            cover_ready=1
                            break
                        fi
                        sleep 0.1
                    done
                    [ "${'$'}cover_ready" = 1 ] || exit 1
                fi
                """.trimIndent()}
                # Finish an interrupted transition that already reached cover-only mode too.
                current=${'$'}(cmd device_state state) || exit 1
                if [ "${'$'}(cat $LEASE_FILE 2>/dev/null)" = '$token' ] &&
                    printf '%s\n' "${'$'}current" | grep -Eq 'Override state:.*(identifier|mIdentifier)=(${if (coverState == null) "$target" else "$target|$coverState"}),' ; then
                    cmd device_state state reset || exit 1
                fi
                [ "${'$'}(cat $LEASE_FILE 2>/dev/null)" != '$token' ] || rm -f $LEASE_FILE
            fi
        """.trimIndent()
    }
}
