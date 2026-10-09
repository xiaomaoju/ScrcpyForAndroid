package io.github.miuzarte.scrcpyforandroid.autocast

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import io.github.miuzarte.scrcpyforandroid.BuildConfig
import io.github.miuzarte.scrcpyforandroid.MainActivity
import io.github.miuzarte.scrcpyforandroid.models.ConnectionTarget
import io.github.miuzarte.scrcpyforandroid.models.DeviceConnectionType
import io.github.miuzarte.scrcpyforandroid.ui.isFlip5CoverDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.NetworkInterface

internal object AutoCastIntents {
    const val ACTION_OPEN = "${BuildConfig.APPLICATION_ID}.OPEN_INNER_SCREEN"
    const val EXTRA_START_APP = "autocast.start_app"
    const val EXTRA_EXTERNAL_INNER = "autocast.external_inner_request"

    fun isAutoCast(intent: Intent?): Boolean = intent?.action == ACTION_OPEN ||
        isExternalInnerRequest(intent)

    // Preserve the legacy launcher's explicit inner-screen request after a force-stop.
    fun isExternalInnerRequest(intent: Intent?): Boolean =
        intent?.component?.className == "${BuildConfig.APPLICATION_ID}.AutoCastActivity"

    suspend fun isLocalTarget(target: ConnectionTarget?): Boolean = withContext(Dispatchers.IO) {
        target != null && target.connectionType == DeviceConnectionType.LAN && target.port in 1..65535 && runCatching {
            val address = InetAddress.getByName(target.host)
            address.isLoopbackAddress || NetworkInterface.getByInetAddress(address) != null
        }.getOrDefault(false)
    }

    fun intent(context: Context, startApp: String? = null): Intent = Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN)
        .putExtra(EXTRA_START_APP, startApp)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun launch(context: Context, startApp: String? = null) {
        val options = ActivityOptions.makeBasic()
        coverDisplay(context)?.let { options.launchDisplayId = it.displayId }
        context.startActivity(intent(context, startApp), options.toBundle())
    }

    fun coverDisplay(context: Context): Display? = context.getSystemService(DisplayManager::class.java)
        .displays.singleOrNull { it.displayId != Display.DEFAULT_DISPLAY &&
            isFlip5CoverDisplay(it.mode.physicalWidth, it.mode.physicalHeight) }

    fun pendingIntent(context: Context): PendingIntent {
        val options = ActivityOptions.makeBasic()
        coverDisplay(context)?.let { options.launchDisplayId = it.displayId }
        return PendingIntent.getActivity(context, 731, intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options.toBundle())
    }
}
