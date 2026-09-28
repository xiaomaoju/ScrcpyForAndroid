package io.github.miuzarte.scrcpyforandroid.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.pages.LocalCoverPanel
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import top.yukonga.miuix.kmp.basic.*

@Composable
fun OverlayIconDropdownMenu(
    entry: DropdownEntry,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxHeight: Dp? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    OverlayIconDropdownMenu(
        entries = listOf(entry),
        modifier = modifier,
        enabled = enabled,
        maxHeight = maxHeight,
        dropdownColors = dropdownColors,
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
        onExpandedChange = onExpandedChange,
        backgroundColor = backgroundColor,
        cornerRadius = cornerRadius,
        minHeight = minHeight,
        minWidth = minWidth,
        content = content,
    )
}

@Composable
fun OverlayIconDropdownMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxHeight: Dp? = null,
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    backgroundColor: Color = Color.Unspecified,
    cornerRadius: Dp = IconButtonDefaults.CornerRadius,
    minHeight: Dp = IconButtonDefaults.MinHeight,
    minWidth: Dp = IconButtonDefaults.MinWidth,
    content: @Composable () -> Unit,
) {
    if (!LocalCoverDisplay.current) {
        top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu(
            entries = entries,
            modifier = modifier,
            enabled = enabled,
            maxHeight = maxHeight,
            dropdownColors = dropdownColors,
            renderInRootScaffold = renderInRootScaffold,
            collapseOnSelection = collapseOnSelection,
            onExpandedChange = onExpandedChange,
            backgroundColor = backgroundColor,
            cornerRadius = cornerRadius,
            minHeight = minHeight,
            minWidth = minWidth,
            content = content,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(
            onClick = { expanded = !expanded; onExpandedChange?.invoke(expanded) },
            enabled = enabled && entries.any { it.enabled && it.items.isNotEmpty() },
            backgroundColor = backgroundColor,
            cornerRadius = cornerRadius,
            minHeight = if (LocalCoverPanel.current) 32.dp else minHeight,
            minWidth = if (LocalCoverPanel.current) 32.dp else minWidth,
            content = content,
        )
        CoverMenuSheet(
            show = expanded,
            entries = entries,
            onDismiss = { expanded = false; onExpandedChange?.invoke(false) },
            renderInRootScaffold = renderInRootScaffold,
            collapseOnSelection = collapseOnSelection,
        )
    }
}
