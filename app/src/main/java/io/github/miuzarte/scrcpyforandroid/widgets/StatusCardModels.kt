package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.constants.UiSpacing
import io.github.miuzarte.scrcpyforandroid.ui.contextClick
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import io.github.miuzarte.scrcpyforandroid.pages.LocalCoverPanel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Immutable
internal data class StatusBigCardSpec(
    val title: String,
    val subtitle: String,
    val containerColor: Color,
    val titleColor: Color,
    val subtitleColor: Color,
    val icon: ImageVector,
    val iconTint: Color,
)

@Immutable
internal data class StatusSmallCardSpec(
    val title: String,
    val value: String,
)

@Immutable
internal data class StatusCardSpec(
    val big: StatusBigCardSpec,
    val firstSmall: StatusSmallCardSpec,
    val secondSmall: StatusSmallCardSpec,
)

@Composable
internal fun StatusCardLayout(
    spec: StatusCardSpec,
    busyLabel: String?,
) {
    val haptic = LocalHapticFeedback.current

    if (LocalCoverDisplay.current) {
        Card(insideMargin = PaddingValues(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(spec.big.icon, null, Modifier.size(18.dp), tint = spec.big.iconTint)
                Text(spec.big.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            if (spec.big.subtitle.isNotBlank()) Text(spec.big.subtitle, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            busyLabel?.let { Text(it, fontSize = 11.sp, color = colorScheme.primary) }
            listOf(spec.firstSmall, spec.secondSmall).forEach { metric ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(metric.title, fontSize = 10.sp, color = colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                    Text(metric.value, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.PageItem),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            colors = defaultColors(color = spec.big.containerColor),
            pressFeedbackType = PressFeedbackType.Tilt,
            onClick = haptic::contextClick,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset(38.dp, 45.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Icon(
                        imageVector = spec.big.icon,
                        contentDescription = null,
                        modifier = Modifier.size(170.dp),
                        tint = spec.big.iconTint,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(UiSpacing.Large),
                ) {
                    Text(
                        text = spec.big.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = spec.big.titleColor,
                    )
                    Spacer(Modifier.height(UiSpacing.Tiny))
                    Text(
                        text = spec.big.subtitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = spec.big.subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (busyLabel != null) {
                        Spacer(Modifier.height(UiSpacing.Small))
                        Text(
                            text = busyLabel,
                            fontSize = 12.sp,
                            color = colorScheme.primary,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            StatusMetricCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                spec = spec.firstSmall,
            )
            Spacer(Modifier.height(UiSpacing.PageItem))
            StatusMetricCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                spec = spec.secondSmall,
            )
        }
    }
}

@Composable
private fun StatusMetricCard(
    modifier: Modifier,
    spec: StatusSmallCardSpec,
) {
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier,
        insideMargin = PaddingValues(UiSpacing.Large),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = haptic::contextClick,
    ) {
        Text(
            text = spec.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = spec.value,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
