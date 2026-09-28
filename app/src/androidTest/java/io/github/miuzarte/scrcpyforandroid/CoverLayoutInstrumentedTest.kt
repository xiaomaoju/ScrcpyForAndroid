package io.github.miuzarte.scrcpyforandroid

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.miuzarte.scrcpyforandroid.scaffolds.AdaptiveDialog
import io.github.miuzarte.scrcpyforandroid.miuix.CoverMenuSheet
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverContentHeight
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverLayoutInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun coverFixesFontScaleWithoutChangingPixelDensity() {
        var actualDensity: Density? = null
        var cover = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, 1.8f)) {
                io.github.miuzarte.scrcpyforandroid.ui.CoverDisplayContent {
                    cover = LocalCoverDisplay.current
                    actualDensity = LocalDensity.current
                }
            }
        }
        compose.runOnIdle {
            // Only the physical 720×748 panel gets fixed fonts; normal displays inherit the setting.
            org.junit.Assert.assertEquals(2.75f, actualDensity!!.density, 0f)
            org.junit.Assert.assertEquals(if (cover) 1f else 1.8f, actualDensity!!.fontScale, 0f)
        }
    }

    @Test
    fun lastChildMenuItemIsReachableWithoutOpeningOutsideTheCover() {
        var selected = -1
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, 1.3f)) {
                MiuixTheme {
                    BoxWithConstraints(Modifier.size(262.dp, 230.dp)) {
                        CompositionLocalProvider(LocalCoverDisplay provides true, LocalCoverContentHeight provides maxHeight) {
                            Scaffold {
                                CoverMenuSheet(
                                    show = true,
                                    entries = listOf(DropdownEntry(listOf(DropdownItem(
                                        text = "更多选项",
                                        children = (0..19).map { index -> DropdownItem(text = "选项 $index", onClick = { selected = index }) },
                                    )))),
                                    onDismiss = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("更多选项").performClick()
        compose.onNodeWithTag("cover-menu-list").performScrollToIndex(19)
        compose.onNodeWithText("选项 19").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(selected == 19) }
    }

    @Test
    fun confirmationStaysVisibleBeforeAndAfterScrollingLongForm() {
        var confirmed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, 1.3f)) {
                MiuixTheme {
                    BoxWithConstraints(Modifier.size(262.dp, 180.dp)) {
                        CompositionLocalProvider(LocalCoverDisplay provides true, LocalCoverContentHeight provides maxHeight) {
                            Scaffold {
                                AdaptiveDialog(show = true, title = "配对", defaultWindowInsetsPadding = false,
                                    actions = { TextButton("确认", onClick = { confirmed = true }) },
                                ) {
                                    repeat(8) { Text("参数 $it", modifier = Modifier.padding(vertical = 12.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val footer = compose.onNodeWithText("确认").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("参数 7").performScrollTo().assertIsDisplayed()
        org.junit.Assert.assertEquals(footer, compose.onNodeWithText("确认").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("确认").performTouchInput { click() }
        compose.runOnIdle { assertTrue(confirmed) }
    }

    @Test
    fun longFormActionRemainsReachableAt440DpiAndLargeFont() {
        var confirmed = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.75f, 1.3f)) {
                MiuixTheme {
                    BoxWithConstraints(Modifier.size(262.dp, 230.dp)) {
                        CompositionLocalProvider(
                            LocalCoverDisplay provides true,
                            LocalCoverContentHeight provides maxHeight,
                        ) {
                            Scaffold {
                                AdaptiveDialog(
                                    show = true,
                                    title = "外屏配对设置",
                                    summary = "较长的说明在小屏和大字体下仍可通过滚动完整读取。",
                                    defaultWindowInsetsPadding = false,
                                ) {
                                    repeat(8) { index -> Text("设置项目 $index", modifier = Modifier.padding(vertical = 12.dp)) }
                                    TextButton(text = "确认配对", onClick = { confirmed = true })
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("确认配对").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(confirmed) }
        compose.onNodeWithText("外屏配对设置").performScrollTo().assertIsDisplayed()
    }
}
