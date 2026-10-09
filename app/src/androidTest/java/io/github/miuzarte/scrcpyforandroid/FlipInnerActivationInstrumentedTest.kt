package io.github.miuzarte.scrcpyforandroid

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import io.github.miuzarte.scrcpyforandroid.widgets.FlipInnerActivationPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FlipInnerActivationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coverPreferenceCanBeTurnedOffAndBackOnFromItsRow() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalCoverDisplay provides true) {
                    Box(Modifier.width(320.dp)) {
                        FlipInnerActivationPreference(enabled.value, manufacturer = "samsung", model = "SM-F7410") { enabled.value = it }
                    }
                }
            }
        }
        compose.onNodeWithTag("activate-flip-inner").assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(enabled.value) }
        compose.onNodeWithTag("activate-flip-inner").performClick()
        compose.runOnIdle { assertTrue(enabled.value) }
    }

    @Test fun unsupportedPhoneStillShowsModelAndCannotEnableActivation() {
        var changed = false
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalCoverDisplay provides true) {
                    Box(Modifier.width(320.dp)) {
                        FlipInnerActivationPreference(true, manufacturer = "Google", model = "Pixel 9") { changed = true }
                    }
                }
            }
        }
        compose.onNodeWithTag("flip-model-status", useUnmergedTree = true).assertIsDisplayed().assertTextContains("Pixel 9", substring = true)
        compose.onNodeWithTag("activate-flip-inner").assertHasNoClickAction()
        compose.onNode(isToggleable()).assertIsNotEnabled().assertIsOff()
        compose.runOnIdle { assertFalse(changed) }
    }

    @Test fun modelStatusRendersBesideTitleWithGreenOrRedTextAndWrapsOnNarrowPanels() {
        val dark = mutableStateOf(false)
        val supported = mutableStateOf(true)
        val width = mutableStateOf(320.dp)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) }
        val localizedContext = context.createConfigurationContext(configuration)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localizedContext, LocalConfiguration provides configuration, LocalCoverDisplay provides true) {
                MiuixTheme(
                    colors = if (dark.value) darkColorScheme() else lightColorScheme(),
                    textStyles = MiuixTheme.textStyles.copy(
                        headline1 = MiuixTheme.textStyles.headline1.copy(fontSize = 14.sp),
                        body2 = MiuixTheme.textStyles.body2.copy(fontSize = 11.sp),
                    ),
                ) {
                    Box(Modifier.width(width.value)) {
                        FlipInnerActivationPreference(true, manufacturer = "samsung", model = if (supported.value) "SM-F7410" else "SM-F721B") {}
                    }
                }
            }
        }
        for (darkMode in listOf(false, true)) for (isSupported in listOf(true, false)) {
            compose.runOnIdle { dark.value = darkMode; supported.value = isSupported }
            val model = if (isSupported) "SM-F7410" else "SM-F721B"
            val status = compose.onNodeWithTag("flip-model-status", useUnmergedTree = true)
            status.assertIsDisplayed().assertTextEquals("$model · ${if (isSupported) "符合条件" else "不符合条件"}")
            val titleBounds = compose.onNodeWithTag("flip-activation-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val statusBounds = status.fetchSemanticsNode().boundsInRoot
            assertTrue("Status stays beside the title on the cover", statusBounds.left >= titleBounds.right && statusBounds.top < titleBounds.bottom)
            val pixels = status.captureToImage().toPixelMap()
            var coloredPixels = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (isSupported && pixel.green > pixel.red + .15f && pixel.green > pixel.blue + .1f ||
                    !isSupported && pixel.red > pixel.green + .15f && pixel.red > pixel.blue + .15f) coloredPixels++
            }
            assertTrue("Status text renders ${if (isSupported) "green" else "red"}", coloredPixels > 20)
            if (InstrumentationRegistry.getArguments().getString("flipModelScreenshots") == "true") {
                File(context.cacheDir, "flip-model-${if (isSupported) "supported" else "unsupported"}-${if (darkMode) "dark" else "light"}.png").outputStream().use {
                    compose.onNodeWithTag("activate-flip-inner").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
        compose.runOnIdle { width.value = 220.dp }
        val titleBounds = compose.onNodeWithTag("flip-activation-title", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val statusBounds = compose.onNodeWithTag("flip-model-status", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Narrow panels wrap the full status below the title", statusBounds.top >= titleBounds.bottom)
    }
}
