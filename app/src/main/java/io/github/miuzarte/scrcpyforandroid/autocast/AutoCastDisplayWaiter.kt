package io.github.miuzarte.scrcpyforandroid.autocast

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Both display discovery and window readiness must remain inside the active fold lease. */
internal object AutoCastDisplayWaiter {
    suspend fun <T : Any> await(
        targetId: Int,
        timeoutMessage: String,
        readFold: suspend () -> AutoCastPolicy.FoldSnapshot,
        findReady: suspend () -> T?,
        timeoutMs: Long = 5_000,
        pollMs: Long = 150,
    ): T {
        val ready = withTimeoutOrNull(timeoutMs) {
            var result: T?
            do {
                val fold = readFold()
                check(AutoCastPolicy.isClosed(fold.base)) { "等待外屏期间手机已展开，已停止本次投屏。" }
                check(AutoCastPolicy.innerDefaultActive(fold, targetId)) { "等待外屏期间双屏模式已退出，已停止本次投屏。" }
                result = findReady()
                if (result == null) delay(pollMs)
            } while (result == null)
            result
        }
        return checkNotNull(ready) { timeoutMessage }
    }
}
