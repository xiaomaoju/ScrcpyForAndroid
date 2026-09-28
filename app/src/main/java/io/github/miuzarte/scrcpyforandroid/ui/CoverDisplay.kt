package io.github.miuzarte.scrcpyforandroid.ui

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.RoundedCorner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

val LocalCoverDisplay = staticCompositionLocalOf { false }
val LocalCoverContentHeight = staticCompositionLocalOf { Dp.Unspecified }

// Identify the physical panel, not a short app window (IME, split screen or PiP).
// Layout continues to use the actual window's dp constraints and system insets.
internal fun isFlip5CoverDisplay(width: Int, height: Int): Boolean =
    minOf(width, height) == 720 && maxOf(width, height) == 748

@Composable
fun CoverDisplayContent(content: @Composable () -> Unit) {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val displayManager = remember(view) { view.context.getSystemService(DisplayManager::class.java) }
    var displayRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(view, displayManager) {
        val listener = object: DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { displayRevision++ }
            override fun onDisplayRemoved(displayId: Int) { displayRevision++ }
            override fun onDisplayChanged(displayId: Int) { displayRevision++ }
        }
        displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }
    val isCover = remember(view, configuration, displayRevision) {
        view.display?.mode?.let { isFlip5CoverDisplay(it.physicalWidth, it.physicalHeight) } == true
    }

    val systemDensity = LocalDensity.current
    // The cover uses fixed typography by design; preserve pixel density and all inner-screen settings.
    val contentDensity = remember(systemDensity, isCover) {
        if (isCover) Density(systemDensity.density, fontScale = 1f) else systemDensity
    }
    CompositionLocalProvider(LocalCoverDisplay provides isCover, LocalDensity provides contentDensity) {
        val cornerInset = if (isCover && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = listOf(
                RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT,
            ).maxOf { view.rootWindowInsets?.getRoundedCorner(it)?.radius ?: 0 }
            // Inset both axes to the inscribed rectangle of the rounded display.
            ceil(radius * (1.0 - kotlin.math.sqrt(0.5))).toInt()
        } else 0
        BoxWithConstraints(
            modifier = if (isCover) Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.union(WindowInsets.ime)
                        .union(WindowInsets(cornerInset, cornerInset, cornerInset, cornerInset)),
                )

            else Modifier.fillMaxSize(),
        ) {
            CompositionLocalProvider(LocalCoverContentHeight provides maxHeight) {
                content()
            }
        }
    }
}

/** Compact preference padding without changing system density or upstream components. */
@Composable
internal fun coverPreferenceMargin(): PaddingValues =
    if (LocalCoverDisplay.current) PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    else top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin
