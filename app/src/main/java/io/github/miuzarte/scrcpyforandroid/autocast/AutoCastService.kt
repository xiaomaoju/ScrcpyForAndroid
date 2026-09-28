package io.github.miuzarte.scrcpyforandroid.autocast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime
import kotlinx.coroutines.*

/** Keeps a user-initiated session/pairing alive; the session itself belongs to AppRuntime. */
class AutoCastService: Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sawOperation = false
    private val controller get() = AppRuntime.obtainAutoCast()
    private val screenOff = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF && controller.state.value.phase == AutoCastPhase.RUNNING) scope.launch {
                delay(400)
                // Device-state transitions can emit SCREEN_OFF for the default/inner display.
                if (controller.state.value.phase == AutoCastPhase.RUNNING &&
                    AutoCastIntents.coverDisplay(context)?.state == Display.STATE_OFF &&
                    !getSystemService(PowerManager::class.java).isInteractive) controller.stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppRuntime.init(applicationContext)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.autocast_name), NotificationManager.IMPORTANCE_DEFAULT),
        )
        startForeground(NOTIFICATION, notification(AutoCastState(AutoCastPhase.CONNECTING)))
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            controller.state.collect { state ->
                AutoCastWidgetProvider.refresh(this@AutoCastService, state)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(state))
                if (state.phase != AutoCastPhase.IDLE) sawOperation = true
                else if (sawOperation) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> controller.stop()
            ACTION_PAIR -> RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PAIRING_CODE)?.let { controller.pair(it.toString()) }
            else -> controller.start(intent?.getBooleanExtra(EXTRA_COVER, false) == true,
                intent?.getStringExtra(AutoCastIntents.EXTRA_START_APP),
                intent?.getBooleanExtra(AutoCastIntents.EXTRA_EXTERNAL_INNER, false) == true)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) { controller.onWindowRemoved() }
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        scope.cancel()
        AppRuntime.autoCast?.let { if (it.active) it.stop() }
        super.onDestroy()
    }

    private fun notification(state: AutoCastState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_autocast)
            .setContentTitle(getString(R.string.autocast_name))
            .setContentText(state.message.ifBlank { getString(state.phase.messageResource()) })
            .setContentIntent(AutoCastIntents.pendingIntent(this))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, getString(R.string.autocast_end), PendingIntent.getService(this, 732,
                Intent(this, AutoCastService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
        if (state.phase == AutoCastPhase.NEEDS_PAIRING) {
            val reply = RemoteInput.Builder(PAIRING_CODE).setLabel(getString(R.string.autocast_pair_code)).build()
            builder.addAction(NotificationCompat.Action.Builder(0, getString(R.string.autocast_pair_code),
                PendingIntent.getService(this, 733, Intent(this, AutoCastService::class.java).setAction(ACTION_PAIR),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
                .addRemoteInput(reply).build())
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL = "flip5_autocast"
        private const val NOTIFICATION = 731
        private const val ACTION_PAIR = "autocast.PAIR"
        private const val ACTION_STOP = "autocast.STOP"
        private const val PAIRING_CODE = "pairing_code"
        private const val EXTRA_COVER = "cover"

        internal fun start(context: Context, cover: Boolean, startApp: String? = null, externalInnerRequest: Boolean = false) {
            ContextCompat.startForegroundService(context, Intent(context, AutoCastService::class.java)
                .putExtra(EXTRA_COVER, cover).putExtra(AutoCastIntents.EXTRA_START_APP, startApp)
                .putExtra(AutoCastIntents.EXTRA_EXTERNAL_INNER, externalInnerRequest))
        }
    }
}
