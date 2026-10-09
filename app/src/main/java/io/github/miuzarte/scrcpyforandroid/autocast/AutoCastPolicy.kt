package io.github.miuzarte.scrcpyforandroid.autocast

/** Flip5/Flip6 decisions. Never infer a fold state from its numeric identifier. */
internal object AutoCastPolicy {
    data class FoldState(val id: Int, val name: String)
    data class FoldSnapshot(val current: FoldState, val base: FoldState, val override: FoldState?)

    private val statePattern = Regex("(?:identifier|mIdentifier)=(\\d+),\\s*(?:name|mName)='([^']+)'")

    // Regional variants share these prefixes; Japanese carrier models use separate names.
    private val modelPrefixes = listOf("SM-F731", "SM-F741")
    private val carrierModels = setOf("SC-54D", "SCG23", "SC-54E", "SCG29")

    fun supports(manufacturer: String, model: String): Boolean =
        manufacturer.equals("samsung", ignoreCase = true) &&
            (modelPrefixes.any { model.startsWith(it, ignoreCase = true) } ||
                model.uppercase() in carrierModels)

    fun routeMainThroughActivation(enabled: Boolean, supported: Boolean, onCover: Boolean, localTarget: Boolean,
        alreadyPrepared: Boolean = false): Boolean = (enabled || alreadyPrepared) && supported && onCover && localTarget

    fun preparedInner(fold: FoldSnapshot): Boolean = innerDefaultActive(fold, dualState(listOf(fold.current))?.id ?: -1)

    fun shouldPrepareInner(enabled: Boolean, externalInnerRequest: Boolean): Boolean = enabled || externalInnerRequest

    fun states(output: String): List<FoldState> = statePattern.findAll(output).map {
        FoldState(it.groupValues[1].toInt(), it.groupValues[2])
    }.toList()

    fun snapshot(output: String): FoldSnapshot {
        fun named(prefix: String): FoldState? = output.lineSequence()
            .firstOrNull { it.trimStart().startsWith(prefix) }?.let { states(it).firstOrNull() }
        val current = requireNotNull(named("Committed state:")) { "Cannot read the current fold state" }
        return FoldSnapshot(current, named("Base state:") ?: current, named("Override state:"))
    }

    fun dualState(states: List<FoldState>): FoldState? = states.singleOrNull {
        val name = it.name.uppercase()
        name.contains("DUAL") || name.contains("CONCURRENT_INNER_DEFAULT")
    }

    // Flip5's cover-only TENT mode avoids the synthetic OPEN -> CLOSED sleep event.
    fun coverOnlyState(states: List<FoldState>): FoldState? = states.singleOrNull {
        it.name.equals("TENT", ignoreCase = true)
    }

    fun isClosed(state: FoldState): Boolean = state.name.uppercase() in
        setOf("CLOSE", "CLOSED", "CLOSE_STATE", "CLOSED_STATE")

    /** Called after committing dual mode and identifying the cover through CoverDisplay. */
    fun displaysReady(sourceId: Int, width: Int, height: Int, coverId: Int, coverOn: Boolean, windowId: Int): Boolean =
        coverOn && windowId == coverId && acceptsCapture(sourceId, windowId, width, height)

    fun innerDefaultActive(fold: FoldSnapshot, expectedId: Int): Boolean =
        expectedId >= 0 && fold.current.id == expectedId && isClosed(fold.base)

    // Dimensions describe the current mode, not the panel's identity. They may match the cover.
    fun acceptsCapture(sourceId: Int, windowId: Int, width: Int, height: Int): Boolean =
        sourceId == 0 && windowId > 0 && sourceId != windowId && width > 0 && height > 0

    fun pairingCode(input: String): String? = input.trim().takeIf { it.matches(Regex("[0-9]{6}")) }
}

internal enum class AutoCastPhase {
    IDLE, CONNECTING, NEEDS_PAIRING, PAIRING, READY, PREPARING, STARTING, RUNNING, RECOVERING, STOPPING, ERROR, UNSUPPORTED,
}

internal data class AutoCastState(
    val phase: AutoCastPhase = AutoCastPhase.IDLE,
    val message: String = "",
    val diagnostics: String = "",
) {
    val busy: Boolean get() = phase in setOf(AutoCastPhase.CONNECTING, AutoCastPhase.PAIRING,
        AutoCastPhase.PREPARING, AutoCastPhase.STARTING, AutoCastPhase.RECOVERING, AutoCastPhase.STOPPING)
}
