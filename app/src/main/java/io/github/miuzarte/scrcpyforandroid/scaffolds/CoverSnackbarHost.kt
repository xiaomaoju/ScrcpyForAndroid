package io.github.miuzarte.scrcpyforandroid.scaffolds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.miuzarte.scrcpyforandroid.R
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Notifications occupy the title strip, leaving the pinned connection actions touchable. */
@Composable
fun CoverSnackbarHost(state: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var expandedMessage by remember { mutableStateOf<String?>(null) }
    SnackbarHost(state, modifier = Modifier.heightIn(max = 40.dp), canSwipeToDismiss = false) { data ->
        Row(Modifier.fillMaxWidth().height(28.dp).background(MiuixTheme.colorScheme.onSurface).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(data.visuals.message, fontSize = 11.sp, color = MiuixTheme.colorScheme.surface,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { expandedMessage = data.visuals.message })
            data.visuals.actionLabel?.let { label ->
                Text(label, fontSize = 11.sp, color = MiuixTheme.colorScheme.surface,
                    modifier = Modifier.clickable { scope.launch { data.performAction() } }.padding(horizontal = 4.dp))
            }
            IconButton(onClick = { scope.launch { data.dismiss() } }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.cd_close), Modifier.size(16.dp), tint = MiuixTheme.colorScheme.surface)
            }
        }
    }
    AdaptiveDialog(show = expandedMessage != null, onDismissRequest = { expandedMessage = null }) {
        Text(expandedMessage.orEmpty())
        TextButton(stringResource(R.string.button_confirm), onClick = { expandedMessage = null }, modifier = Modifier.fillMaxWidth())
    }
}
