package io.github.miuzarte.scrcpyforandroid.scrcpy

import kotlin.math.hypot

internal data class CoverPoint(val x: Float, val y: Float) {
    fun clamped() = CoverPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

internal sealed interface CoverInput {
    data class Touch(val action: Int, val id: Long, val point: CoverPoint, val mouse: Boolean = false) : CoverInput
    data class Scroll(val point: CoverPoint, val horizontal: Float, val vertical: Float) : CoverInput
    data class Key(val action: Int, val code: Int) : CoverInput
}

/** Platform-independent gesture state. Positions are normalized to the actual video. */
internal class CoverInputController(
    private val emit: (List<CoverInput>) -> Unit,
    private val cursorChanged: (CoverPoint) -> Unit,
    private val hoverEnabled: Boolean = true,
) {
    var cursor = CoverPoint(.5f, .5f)
        private set
    private val fingers = linkedMapOf<Int, CoverPoint>()
    private var padOrigin: CoverPoint? = null
    private var padLast: CoverPoint? = null
    private var padMoved = false
    private var padHadMultiplePointers = false
    private var dragging = false

    fun touchDown(id: Int, point: CoverPoint) {
        if (point.x !in 0f..1f || point.y !in 0f..1f || id in fingers) return
        fingers[id] = point
        emit(listOf(CoverInput.Touch(DOWN, id.toLong(), point)))
    }

    fun touchMove(points: Map<Int, CoverPoint>) {
        val events = points.mapNotNull { (id, raw) ->
            if (id !in fingers) return@mapNotNull null
            val point = raw.clamped()
            fingers[id] = point
            CoverInput.Touch(MOVE, id.toLong(), point)
        }
        if (events.isNotEmpty()) emit(events)
    }

    fun touchUp(id: Int, point: CoverPoint) {
        if (fingers.remove(id) != null) emit(listOf(CoverInput.Touch(UP, id.toLong(), point.clamped())))
    }

    fun padDown(point: CoverPoint) {
        padOrigin = point
        padLast = point
        padMoved = false
        padHadMultiplePointers = false
    }

    /** Call on every pointer-count change; lifting a finger must not create a cursor jump. */
    fun padPointersChanged(center: CoverPoint?) {
        if (dragging) emit(listOf(CoverInput.Touch(UP, MOUSE_ID, cursor, true)))
        dragging = false
        padHadMultiplePointers = true
        padLast = center
    }

    fun padMove(center: CoverPoint, count: Int, slop: Float) {
        val last = padLast ?: return
        padLast = center
        val dx = center.x - last.x
        val dy = center.y - last.y
        val origin = padOrigin ?: return
        if (hypot(center.x - origin.x, center.y - origin.y) > slop) padMoved = true
        if (count > 1) {
            padHadMultiplePointers = true
            emit(listOf(CoverInput.Scroll(cursor, (-dx * 5f).coerceIn(-1f, 1f), (-dy * 5f).coerceIn(-1f, 1f))))
        } else {
            // Pointer changes rebase padLast. Resume with the remaining finger;
            // multi-touch history suppresses clicks/long presses, not cursor motion.
            cursor = CoverPoint(cursor.x + dx, cursor.y + dy).clamped()
            cursorChanged(cursor)
            if (dragging || hoverEnabled) emit(listOf(CoverInput.Touch(if (dragging) MOVE else HOVER, MOUSE_ID, cursor, true)))
        }
    }

    fun padLongPress() {
        if (padOrigin != null && !padMoved && !padHadMultiplePointers && !dragging) {
            dragging = true
            emit(listOf(CoverInput.Touch(DOWN, MOUSE_ID, cursor, true)))
        }
    }

    fun padUp() {
        when {
            dragging -> emit(listOf(CoverInput.Touch(UP, MOUSE_ID, cursor, true)))
            padOrigin != null && !padMoved && !padHadMultiplePointers -> emit(listOf(
                CoverInput.Touch(DOWN, MOUSE_ID, cursor, true),
                CoverInput.Touch(UP, MOUSE_ID, cursor, true),
            ))
        }
        resetPad()
    }

    fun scroll(horizontal: Float, vertical: Float) {
        emit(listOf(CoverInput.Scroll(cursor, horizontal.coerceIn(-1f, 1f), vertical.coerceIn(-1f, 1f))))
    }

    fun key(code: Int) = emit(listOf(CoverInput.Key(DOWN, code), CoverInput.Key(UP, code)))

    fun cancel() {
        val releases = fingers.map { (id, point) -> CoverInput.Touch(UP, id.toLong(), point) }.toMutableList()
        fingers.clear()
        if (dragging) releases += CoverInput.Touch(UP, MOUSE_ID, cursor, true)
        resetPad()
        if (releases.isNotEmpty()) emit(releases)
    }

    private fun resetPad() {
        padOrigin = null
        padLast = null
        padMoved = false
        padHadMultiplePointers = false
        dragging = false
    }

    companion object {
        const val DOWN = 0
        const val UP = 1
        const val MOVE = 2
        const val HOVER = 7
        const val MOUSE_ID = -1L
    }
}

internal fun coverVideoPaneWidth(videoWidth: Int, videoHeight: Int, width: Float, height: Float, rightMin: Float, gap: Float): Float {
    if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return 0f
    return minOf(height * videoWidth / videoHeight, (width - rightMin - gap).coerceAtLeast(0f))
}
