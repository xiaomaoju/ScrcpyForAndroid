package io.github.miuzarte.scrcpyforandroid.scrcpy

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** One writer preserves down/move/up order. Only consecutive motion batches are coalesced. */
internal class CoverInputDispatcher(private val send: suspend (CoverInput) -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val pending = ArrayDeque<List<CoverInput>>()
    private var closed = false

    init {
        scope.launch {
            try {
                for (ignored in wake) {
                    while (true) {
                        val batch = synchronized(pending) { pending.removeFirstOrNull() } ?: break
                        for (event in batch) runCatching { send(event) }.onFailure {
                            if (it is CancellationException) throw it
                            Log.w("CoverInput", "Input delivery failed", it)
                        }
                    }
                    if (synchronized(pending) { closed && pending.isEmpty() }) break
                }
            } finally {
                wake.close()
                scope.cancel()
            }
        }
    }

    fun submit(events: List<CoverInput>) {
        synchronized(pending) {
            if (closed) return
            if (isMotion(events) && pending.lastOrNull()?.let(::isMotion) == true) pending.removeLast()
            pending.addLast(events)
        }
        wake.trySend(Unit)
    }

    /** Release events are submitted before close; drain them instead of cancelling the writer. */
    fun close() {
        synchronized(pending) { closed = true }
        wake.trySend(Unit)
    }

    private fun isMotion(events: List<CoverInput>) = events.isNotEmpty() && events.all {
        it is CoverInput.Touch && (it.action == CoverInputController.MOVE || it.action == CoverInputController.HOVER)
    }
}
