package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.ui.coverPreferenceMargin
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
internal fun FlipInnerActivationPreference(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Card {
        SwitchPreference(
            modifier = Modifier.testTag("activate-flip-inner"),
            title = stringResource(R.string.device_activate_flip_inner),
            summary = stringResource(R.string.device_activate_flip_inner_summary),
            insideMargin = coverPreferenceMargin(),
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
