package io.github.miuzarte.scrcpyforandroid.scrcpy

import kotlin.math.roundToInt

/** Reserve a usable detail panel, with a small dead band to avoid threshold oscillation. */
internal fun coverNeedsControlRail(videoWidth: Int, videoHeight: Int, width: Float, height: Float, wasRail: Boolean? = null): Boolean {
    if (videoWidth <= 0 || videoHeight <= 0 || width <= 0 || height <= 0) return false
    val remaining = width - height * videoWidth / videoHeight - 4f
    val minimum = when (wasRail) { true -> 128f; false -> 112f; null -> 120f }
    return remaining < minimum
}

/** Fit and input mapping share the same viewport, after all safe-area/control padding. */
internal data class VideoContentBounds(val width: Float, val height: Float, val left: Float, val top: Float) {
    fun toVideoPosition(x: Float, y: Float, videoWidth: Int, videoHeight: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 0 to 0
        val normalizedX = ((x - left) / width).coerceIn(0f, 1f)
        val normalizedY = ((y - top) / height).coerceIn(0f, 1f)
        return (normalizedX * (videoWidth - 1).coerceAtLeast(0)).roundToInt() to
            (normalizedY * (videoHeight - 1).coerceAtLeast(0)).roundToInt()
    }
}

internal fun fitVideoContent(videoWidth: Int, videoHeight: Int, viewportWidth: Int, viewportHeight: Int): VideoContentBounds {
    if (videoWidth <= 0 || videoHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
        return VideoContentBounds(0f, 0f, 0f, 0f)
    }
    val scale = minOf(viewportWidth.toFloat() / videoWidth, viewportHeight.toFloat() / videoHeight)
    val width = videoWidth * scale
    val height = videoHeight * scale
    return VideoContentBounds(width, height, (viewportWidth - width) / 2f, (viewportHeight - height) / 2f)
}
