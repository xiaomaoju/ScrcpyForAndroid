package io.github.miuzarte.scrcpyforandroid.miuix

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverContentHeight
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** Cover menus use one bounded pane; child menus never open beside the physical screen. */
@Composable
internal fun CoverMenuSheet(
    show: Boolean,
    entries: List<DropdownEntry>,
    onDismiss: () -> Unit,
    onDismissFinished: (() -> Unit)? = null,
    collapseOnSelection: Boolean = true,
    renderInRootScaffold: Boolean = true,
) {
    var path by remember(show) { mutableStateOf(emptyList<Int>()) }
    var items = entries.flatMap { entry -> entry.items.map { it.copy(enabled = entry.enabled && it.enabled) } }
    var title: String? = null
    for (index in path) {
        val parent = items.getOrNull(index)
        title = parent?.text
        items = parent?.children.orEmpty()
    }
    OverlayBottomSheet(
        show = show,
        title = title,
        defaultWindowInsetsPadding = false,
        insideMargin = DpSize(8.dp, 8.dp),
        outsideMargin = DpSize(4.dp, 4.dp),
        renderInRootScaffold = renderInRootScaffold,
        onDismissRequest = onDismiss,
        onDismissFinished = onDismissFinished,
        startAction = if (path.isNotEmpty()) {
            { IconButton(onClick = { path = path.dropLast(1) }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back))
            } }
        } else null,
        endAction = { IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, stringResource(R.string.cd_close))
        } },
    ) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = (LocalCoverContentHeight.current - 64.dp).coerceAtLeast(1.dp)).testTag("cover-menu-list")) {
            itemsIndexed(items) { index, item ->
                BasicComponent(
                    title = item.text,
                    summary = item.summary,
                    enabled = item.enabled,
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    startAction = item.icon?.let { icon -> { icon(Modifier.size(24.dp)) } },
                    endActions = {
                        if (!item.children.isNullOrEmpty()) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        else if (item.selected) Icon(Icons.Rounded.Check, null)
                    },
                    onClick = {
                        if (!item.children.isNullOrEmpty()) path = path + index
                        else {
                            item.onClick?.invoke()
                            if (collapseOnSelection) onDismiss()
                        }
                    },
                )
            }
        }
    }
}
