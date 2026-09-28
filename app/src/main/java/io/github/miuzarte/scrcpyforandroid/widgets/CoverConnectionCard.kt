package io.github.miuzarte.scrcpyforandroid.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The primary connection controls stay above the independently scrolling parameters. */
@Composable
internal fun CoverConnectionCard(
    address: String,
    connected: Boolean,
    connecting: Boolean,
    running: Boolean,
    busy: Boolean,
    canConnect: Boolean,
    canFullscreen: Boolean,
    onAddressChange: (String) -> Unit,
    onSaveAddress: () -> Unit,
    onConnect: () -> Unit,
    onCancelConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFullscreen: () -> Unit,
    onPair: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val colors = MiuixTheme.colorScheme
    val buttonStyle = MiuixTheme.textStyles.button.copy(fontSize = if (compact) 12.sp else 13.sp)
    val buttonMargin = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
    Card(modifier = modifier.testTag("cover-connection"), insideMargin = PaddingValues(6.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp)
                    .background(colors.surfaceContainer, RoundedCornerShape(10.dp)).padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    singleLine = true,
                    readOnly = connected || connecting,
                    textStyle = TextStyle(fontSize = if (compact) 12.sp else 13.sp, color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp).testTag("cover-address")
                        .onFocusChanged { if (!it.isFocused) onSaveAddress() },
                    decorationBox = { field ->
                        Box {
                            if (address.isEmpty()) Text(stringResource(R.string.label_ip_port), fontSize = 13.sp, color = colors.onSurfaceVariantSummary)
                            field()
                        }
                    },
                )
                IconButton(onClick = { focus.clearFocus(); keyboard?.hide(); onPair() }, enabled = !busy && !connecting && !connected, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.QrCode2, stringResource(R.string.cd_show_qr_pairing), Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    text = stringResource(if (connecting) R.string.button_cancel else if (connected) R.string.button_disconnect else R.string.button_connect),
                    onClick = { focus.clearFocus(); keyboard?.hide(); if (connecting) onCancelConnect() else if (connected) onDisconnect() else onConnect() },
                    enabled = connecting || (!busy && (connected || canConnect)),
                    modifier = Modifier.weight(1f).testTag("cover-connect"),
                    minHeight = 36.dp, insideMargin = buttonMargin, textStyle = buttonStyle,
                )
                TextButton(
                    text = stringResource(if (running) R.string.button_stop else R.string.button_start),
                    onClick = { focus.clearFocus(); keyboard?.hide(); if (running) onStop() else onStart() },
                    enabled = connected && !connecting && !busy,
                    modifier = Modifier.weight(1f).testTag(if (running) "cover-stop" else "cover-start"),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    minHeight = 36.dp, insideMargin = buttonMargin, textStyle = buttonStyle,
                )
                if (canFullscreen) TextButton(
                    text = stringResource(R.string.button_fullscreen), onClick = onFullscreen, enabled = !busy,
                    modifier = Modifier.weight(1f).testTag("cover-open-fullscreen"),
                    minHeight = 36.dp, insideMargin = buttonMargin, textStyle = buttonStyle,
                )
            }
        }
    }
}
