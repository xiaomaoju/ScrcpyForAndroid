package io.github.miuzarte.scrcpyforandroid.autocast

import io.github.miuzarte.scrcpyforandroid.ui.isFlip5CoverDisplay

/** Shell-visible logical displays can differ from the application's DisplayManager list. */
internal object AutoCastSystemDisplays {
    private val headerPattern = Regex("Display (\\d+):")
    private val idPattern = Regex("\\bdisplayId (\\d+)(?:,| )")
    private val sizePattern = Regex("\\breal (\\d+) x (\\d+)(?:,| )")
    private val statePattern = Regex("\\bstate ([A-Z_]+)(?:,|\\s*\\})")
    private val typePattern = Regex("\\btype ([A-Z_]+)(?:,|\\s*\\})")

    data class LogicalDisplay(val id: Int, val width: Int, val height: Int, val state: String,
        val type: String, val enabled: Boolean?, val inTransition: Boolean?) {
        val ready: Boolean get() = state == "ON" && enabled != false && inTransition != true
    }

    fun parse(dump: String): List<LogicalDisplay> {
        val result = mutableListOf<LogicalDisplay>()
        var blockId: Int? = null
        var enabled: Boolean? = null
        var inTransition: Boolean? = null
        for (raw in dump.lineSequence()) {
            val line = raw.trim()
            val header = headerPattern.matchEntire(line)
            if (header != null) {
                blockId = header.groupValues[1].toIntOrNull()
                enabled = null
                inTransition = null
            } else if (line.startsWith("mIsEnabled=")) {
                enabled = line.substringAfter('=').toBooleanStrictOrNull()
            } else if (line.startsWith("mIsInTransition=")) {
                inTransition = line.substringAfter('=').toBooleanStrictOrNull()
            } else if (line.startsWith("mBaseDisplayInfo=DisplayInfo{")) {
                // Ignore physical device records and per-app overrides. Strip the quoted name
                // before matching fields, and require agreement with the enclosing logical ID.
                val fields = line.substringAfter("\",", "")
                val id = idPattern.find(fields)?.groupValues?.get(1)?.toIntOrNull() ?: continue
                val size = sizePattern.find(fields) ?: continue
                val width = size.groupValues[1].toIntOrNull() ?: continue
                val height = size.groupValues[2].toIntOrNull() ?: continue
                val state = statePattern.find(fields)?.groupValues?.get(1) ?: continue
                val type = typePattern.find(fields)?.groupValues?.get(1) ?: continue
                if (id != blockId || width <= 0 || height <= 0) continue
                result += LogicalDisplay(id, width, height, state, type, enabled, inTransition)
            }
        }
        return result
    }

    fun cover(displays: List<LogicalDisplay>): LogicalDisplay? = displays.singleOrNull {
        it.id > 0 && it.type == "INTERNAL" && isFlip5CoverDisplay(it.width, it.height)
    }?.takeIf { cover -> displays.count { it.id == cover.id } == 1 }

    fun windowReady(displays: List<LogicalDisplay>, destinationId: Int, windowId: Int): Boolean {
        val inner = displays.singleOrNull { it.id == 0 }?.takeIf { it.type == "INTERNAL" } ?: return false
        val cover = cover(displays) ?: return false
        return cover.id == destinationId && AutoCastPolicy.displaysReady(inner.id, inner.width, inner.height,
            cover.id, cover.ready, windowId)
    }

    fun summary(displays: List<LogicalDisplay>): String = displays.take(6).joinToString("; ") {
        "id=${it.id}, ${it.width}x${it.height}, state=${it.state}, type=${it.type}, enabled=${it.enabled}, transition=${it.inTransition}"
    }.ifEmpty { "no valid logical display records" }
}
