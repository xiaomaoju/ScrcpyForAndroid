package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverContentHeight
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.layout.DialogDefaults
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** For finite bottom-sheet bodies without an existing lazy/scroll container. */
@Composable
fun CoverScrollableContent(content: @Composable () -> Unit) {
    if (LocalCoverDisplay.current) {
        BoxWithConstraints {
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                content()
            }
        }
    } else content()
}

/** Cover forms scroll independently of optional pinned confirmation actions. */
@Composable
fun AdaptiveDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    defaultWindowInsetsPadding: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isCover = LocalCoverDisplay.current
    OverlayDialog(
        show = show,
        title = if (isCover) null else title,
        summary = if (isCover) null else summary,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        insideMargin = if (isCover) DpSize(12.dp, 12.dp) else DialogDefaults.insideMargin,
        outsideMargin = if (isCover) DpSize(4.dp, 4.dp) else DialogDefaults.outsideMargin,
    ) {
        if (isCover) {
            Column(Modifier.heightIn(max = (LocalCoverContentHeight.current - 32.dp).coerceAtLeast(1.dp))) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    title?.let {
                        Text(it, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    summary?.let {
                        Text(it, style = MiuixTheme.textStyles.footnote1, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    content()
                }
                actions?.invoke()
            }
        } else {
            content()
            actions?.invoke()
        }
    }
}
