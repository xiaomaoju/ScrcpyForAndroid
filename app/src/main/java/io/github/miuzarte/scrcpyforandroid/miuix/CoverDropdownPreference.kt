package io.github.miuzarte.scrcpyforandroid.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import io.github.miuzarte.scrcpyforandroid.pages.LocalCoverPanel
import top.yukonga.miuix.kmp.basic.*

@Composable
fun OverlayDropdownPreference(
    items: List<String>,
    selectedIndex: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onSelectedIndexChange: ((Int) -> Unit)? = null,
) {
    if (LocalCoverDisplay.current) {
        OverlaySpinnerPreference(
            items = remember(items) { items.map { SpinnerEntry(title = it) } },
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            spinnerColors = dropdownColors,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            renderInRootScaffold = renderInRootScaffold,
            onExpandedChange = onExpandedChange,
            onSelectedIndexChange = onSelectedIndexChange,
        )
    } else {
        top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
            items = items,
            selectedIndex = selectedIndex,
            title = title,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            dropdownColors = dropdownColors,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            renderInRootScaffold = renderInRootScaffold,
            onExpandedChange = onExpandedChange,
            onSelectedIndexChange = onSelectedIndexChange,
        )
    }
}

@Composable
fun OverlayDropdownPreference(
    entries: List<DropdownEntry>,
    title: String,
    modifier: Modifier = Modifier,
    titleColor: BasicComponentColors = BasicComponentDefaults.titleColor(),
    summary: String? = null,
    summaryColor: BasicComponentColors = BasicComponentDefaults.summaryColor(),
    dropdownColors: DropdownColors = DropdownDefaults.dropdownColors(),
    startAction: @Composable (() -> Unit)? = null,
    bottomAction: (@Composable () -> Unit)? = null,
    insideMargin: PaddingValues = BasicComponentDefaults.InsideMargin,
    maxHeight: Dp? = null,
    enabled: Boolean = true,
    showValue: Boolean = true,
    renderInRootScaffold: Boolean = true,
    collapseOnSelection: Boolean = entries.size <= 1,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    if (!LocalCoverDisplay.current) {
        top.yukonga.miuix.kmp.preference.OverlayDropdownPreference(
            entries = entries,
            title = title,
            modifier = modifier,
            titleColor = titleColor,
            summary = summary,
            summaryColor = summaryColor,
            dropdownColors = dropdownColors,
            startAction = startAction,
            bottomAction = bottomAction,
            insideMargin = insideMargin,
            maxHeight = maxHeight,
            enabled = enabled,
            showValue = showValue,
            renderInRootScaffold = renderInRootScaffold,
            collapseOnSelection = collapseOnSelection,
            onExpandedChange = onExpandedChange,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val inCoverPanel = LocalCoverPanel.current
    val selected = entries.flatMap { it.items }.filter { it.selected }.joinToString("\n") { it.text }
    BasicComponent(
        modifier = modifier,
        title = title,
        titleColor = titleColor,
        summary = summary,
        summaryColor = summaryColor,
        startAction = startAction,
        bottomAction = if (inCoverPanel) ({
            Column {
                if (showValue && selected.isNotEmpty()) Text(selected, style = MiuixTheme.textStyles.body2)
                bottomAction?.invoke()
            }
        }) else bottomAction,
        insideMargin = if (LocalCoverDisplay.current) PaddingValues(horizontal = 8.dp, vertical = 6.dp) else insideMargin,
        enabled = enabled && entries.any { it.enabled && it.items.isNotEmpty() },
        onClick = { expanded = !expanded; onExpandedChange?.invoke(expanded) },
        endActions = {
            if (!inCoverPanel && showValue && selected.isNotEmpty()) Text(selected, modifier = Modifier.padding(end = 8.dp), style = MiuixTheme.textStyles.body2)
            DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions)
        },
    )
    CoverMenuSheet(
        show = expanded,
        entries = entries,
        onDismiss = { expanded = false; onExpandedChange?.invoke(false) },
        renderInRootScaffold = renderInRootScaffold,
        collapseOnSelection = collapseOnSelection,
    )
}
