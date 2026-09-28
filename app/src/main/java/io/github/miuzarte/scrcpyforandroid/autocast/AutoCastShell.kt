package io.github.miuzarte.scrcpyforandroid.autocast

import io.github.miuzarte.scrcpyforandroid.nativecore.NativeAdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Used only after the controller has established a loopback ADB connection. */
internal object AutoCastShell {
    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        val marker = "__AUTOCAST_EXIT__"
        NativeAdbService.openShellStream("$command\nprintf '\\n$marker%s\\n' \"\$?\"").use { stream ->
            val output = withTimeout(6_000) {
                runInterruptible { stream.inputStream.bufferedReader().readText() }
            }
            check(output.trimEnd().endsWith("${marker}0")) { output.substringBefore(marker).trim().take(300) }
            output.substringBefore(marker).trim()
        }
    }
}
