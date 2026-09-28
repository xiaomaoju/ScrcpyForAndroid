package io.github.miuzarte.scrcpyforandroid.autocast

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.pages.CoverControlScreen
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import io.github.miuzarte.scrcpyforandroid.storage.Storage
import io.github.miuzarte.scrcpyforandroid.ui.LocalCoverDisplay
import io.github.miuzarte.scrcpyforandroid.ui.createThemeController
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal fun AutoCastPhase.messageResource(): Int = when (this) {
    AutoCastPhase.IDLE -> R.string.autocast_open
    AutoCastPhase.CONNECTING -> R.string.autocast_connecting
    AutoCastPhase.NEEDS_PAIRING -> R.string.autocast_setup
    AutoCastPhase.PAIRING -> R.string.autocast_pairing
    AutoCastPhase.READY -> R.string.autocast_ready
    AutoCastPhase.PREPARING -> R.string.autocast_preparing
    AutoCastPhase.STARTING -> R.string.autocast_starting
    AutoCastPhase.RUNNING -> R.string.autocast_running
    AutoCastPhase.RECOVERING -> R.string.autocast_recovering
    AutoCastPhase.STOPPING -> R.string.autocast_stopping
    AutoCastPhase.ERROR -> R.string.autocast_failed
    AutoCastPhase.UNSUPPORTED -> R.string.autocast_unsupported
}

@Composable
internal fun AutoCastScreen(requestId: Int = 0, startApp: String? = null, externalInnerRequest: Boolean = false, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val context = LocalContext.current
    val isCover = LocalCoverDisplay.current
    val controller = remember { AppRuntime.obtainAutoCast() }
    val displayId = LocalView.current.display?.displayId ?: -1
    val state by controller.state.collectAsState()
    val session by controller.scrcpy.currentSessionState.collectAsState()
    val settings by Storage.appSettings.bundleState.collectAsState()
    val theme = remember(settings) { settings.createThemeController() }
    var closeRequested by rememberSaveable { mutableStateOf(false) }
    var openAppRequested by rememberSaveable { mutableStateOf(false) }
    var wasRunning by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionDenied by rememberSaveable { mutableStateOf(false) }

    fun end(openApp: Boolean = false) {
        closeRequested = true
        openAppRequested = openApp
        controller.stop()
    }

    LaunchedEffect(requestId, isCover, displayId) {
        controller.playbackDisplayId = displayId
        if (controller.supported) AutoCastService.start(context, isCover, startApp, externalInnerRequest) else controller.start(isCover)
    }
    LaunchedEffect(state.phase) {
        if (state.phase == AutoCastPhase.RUNNING) wasRunning = true
        if (state.phase == AutoCastPhase.IDLE && (closeRequested || wasRunning)) {
            if (openAppRequested) onOpenApp() else onClose()
        }
    }
    BackHandler { end() }

    fun openWirelessSettings() {
        val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS")
        if (intent.resolveActivity(context.packageManager) == null) intent.action = Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        context.startActivity(intent)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openWirelessSettings()
        notificationPermissionDenied = !granted
    }

    MiuixTheme(controller = theme) {
        MaterialTheme(colorScheme = if (isCover || isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground) {
            if (isCover && session != null && state.phase in setOf(AutoCastPhase.STARTING, AutoCastPhase.RUNNING)) {
                Box(Modifier.fillMaxSize()) {
                    CoverControlScreen(controller.scrcpy, session, listOf(stringResource(R.string.autocast_name)), {},
                        interactionEnabled = state.phase == AutoCastPhase.RUNNING,
                        onLeave = { end() }, exitLabel = stringResource(R.string.autocast_end), software = {
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.autocast_running))
                                Text(stringResource(R.string.autocast_control_hint), style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { end() }) { Text(stringResource(R.string.autocast_end)) }
                            }
                        })
                    if (state.phase == AutoCastPhase.STARTING) {
                        Surface(Modifier.align(Alignment.Center).testTag("autocast-first-frame"), tonalElevation = 4.dp) {
                            Text(stringResource(R.string.autocast_starting), Modifier.padding(16.dp))
                        }
                    }
                }
            } else {
                AutoCastStatusScreen(if (notificationPermissionDenied) state.copy(message = stringResource(R.string.autocast_notification_needed)) else state,
                    onRetry = { AutoCastService.start(context, isCover, startApp, externalInnerRequest) },
                    onSetup = {
                        if (Build.VERSION.SDK_INT >= 33 && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                            if (notificationPermissionDenied) context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationPermissionDenied = false
                            openWirelessSettings()
                        }
                    },
                    onEnd = { end() }, onOpenApp = { end(openApp = true) })
            }
            }
        }
    }
}

@Composable
internal fun AutoCastStatusScreen(state: AutoCastState, onRetry: () -> Unit, onSetup: () -> Unit, onEnd: () -> Unit, onOpenApp: () -> Unit) {
    val context = LocalContext.current
    var diagnosticsCopied by remember(state.diagnostics) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
        .padding(20.dp).testTag("autocast-status"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.autocast_name), style = MaterialTheme.typography.titleLarge)
        if (state.busy) CircularProgressIndicator(Modifier.size(28.dp))
        Text(stringResource(state.phase.messageResource()), style = MaterialTheme.typography.bodyLarge)
        if (state.message.isNotBlank()) Text(state.message, style = MaterialTheme.typography.bodySmall)
        if (state.phase == AutoCastPhase.NEEDS_PAIRING) {
            Text(stringResource(R.string.autocast_setup_hint), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSetup, modifier = Modifier.fillMaxWidth().testTag("autocast-setup")) {
                Text(stringResource(R.string.autocast_setup_action))
            }
        }
        if (state.phase == AutoCastPhase.READY) Text(stringResource(R.string.autocast_add_widget), style = MaterialTheme.typography.bodySmall)
        if (!state.busy && state.diagnostics.isNotBlank()) {
            OutlinedButton(onClick = {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Flip5 AutoCast", state.diagnostics))
                diagnosticsCopied = true
            }, modifier = Modifier.fillMaxWidth().testTag("autocast-copy-diagnostics")) {
                Text(stringResource(if (diagnosticsCopied) R.string.autocast_diagnostics_copied else R.string.autocast_copy_diagnostics))
            }
        }
        if (state.phase in setOf(AutoCastPhase.ERROR, AutoCastPhase.NEEDS_PAIRING)) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.autocast_retry)) }
        }
        TextButton(onClick = onEnd, enabled = state.phase != AutoCastPhase.STOPPING) { Text(stringResource(R.string.autocast_close)) }
        if (!state.busy) TextButton(onClick = onOpenApp) { Text(stringResource(R.string.autocast_original_app)) }
    }
}
