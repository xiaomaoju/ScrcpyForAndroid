package io.github.miuzarte.scrcpyforandroid.widgets

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.autocast.AutoCastPolicy
import io.github.miuzarte.scrcpyforandroid.ui.coverPreferenceMargin
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlipInnerActivationPreference(
    checked: Boolean,
    manufacturer: String = Build.MANUFACTURER,
    model: String = Build.MODEL,
    onCheckedChange: (Boolean) -> Unit,
) {
    val supported = AutoCastPolicy.supports(manufacturer, model)
    val colors = MiuixTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val statusColor = if (supported) {
        if (dark) Color(0xFF66DD88) else Color(0xFF167337)
    } else {
        if (dark) Color(0xFFFF8A80) else Color(0xFFB3261E)
    }
    Card {
        BasicComponent(
            modifier = Modifier.testTag("activate-flip-inner"),
            insideMargin = coverPreferenceMargin(),
            enabled = supported,
            role = Role.Switch,
            onClick = { onCheckedChange(!checked) },
            endActions = {
                Switch(checked = supported && checked, enabled = supported, onCheckedChange = onCheckedChange)
            },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.device_activate_flip_inner),
                    modifier = Modifier.testTag("flip-activation-title"),
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(if (supported) R.string.device_flip_model_supported else R.string.device_flip_model_unsupported, model),
                    modifier = Modifier.testTag("flip-model-status"),
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = statusColor,
                )
            }
            Text(
                text = stringResource(R.string.device_activate_flip_inner_summary),
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = colors.onSurfaceVariantSummary,
            )
        }
    }
}
