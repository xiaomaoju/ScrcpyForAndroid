package io.github.miuzarte.scrcpyforandroid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import io.github.miuzarte.scrcpyforandroid.widgets.FlipInnerActivationPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(AndroidJUnit4::class)
class FlipInnerActivationInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coverPreferenceCanBeTurnedOffAndBackOnFromItsRow() {
        val enabled = mutableStateOf(true)
        compose.setContent {
            MiuixTheme {
                CompositionLocalProvider(LocalCoverDisplay provides true) {
                    Box(Modifier.width(320.dp)) {
                        FlipInnerActivationPreference(enabled.value) { enabled.value = it }
                    }
                }
            }
        }
        compose.onNodeWithTag("activate-flip-inner").assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(enabled.value) }
        compose.onNodeWithTag("activate-flip-inner").performClick()
        compose.runOnIdle { assertTrue(enabled.value) }
    }
}
