package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import io.github.miuzarte.scrcpyforandroid.pages.LocalCoverPanel
import io.github.miuzarte.scrcpyforandroid.pages.LocalCoverPagePicker
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AdaptiveTopAppBar(
    title: String,
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    bottomContent: @Composable () -> Unit = {},
) {
    if (LocalCoverPanel.current) {
        CoverPanelTopAppBar(title, color, navigationIcon, actions, bottomContent)
    } else if (LocalCoverDisplay.current) {
        CoverPanelTopAppBar(title, color, navigationIcon, actions, bottomContent)
    } else {
        TopAppBar(
            title = title,
            color = color,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            bottomContent = bottomContent,
        )
    }
}

@Composable
fun AdaptiveSmallTopAppBar(
    title: String,
    color: Color = MiuixTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (LocalCoverDisplay.current) CoverPanelTopAppBar(title, color, navigationIcon, actions, bottomContent)
    else SmallTopAppBar(title = title, color = color, navigationIcon = navigationIcon, actions = actions, bottomContent = bottomContent)
}

@Composable
private fun CoverPanelTopAppBar(
    title: String, color: Color,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    bottomContent: @Composable () -> Unit,
) {
    val openPages = LocalCoverPagePicker.current
    val panel = LocalCoverPanel.current
    Column(Modifier.fillMaxWidth().background(color)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.widthIn(max = 32.dp)) { navigationIcon() }
            Row(Modifier.weight(1f).fillMaxHeight().then(if (panel) Modifier.clickable(onClick = openPages) else Modifier).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (panel) Text("⌄", fontSize = 12.sp)
            }
            Row(Modifier.widthIn(max = if (panel) 64.dp else 96.dp), content = actions)
        }
        bottomContent()
    }
}
