package io.github.miuzarte.scrcpyforandroid.autocast

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime

/** An action tile: every tap opens/continues the same operation as the cover card. */
class AutoCastTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val current = AppRuntime.autoCast?.state?.value ?: AutoCastState()
        qsTile?.apply {
            label = getString(R.string.autocast_tile_name)
            state = if (current.phase == AutoCastPhase.RUNNING) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            contentDescription = getString(if (current.phase == AutoCastPhase.RUNNING) R.string.autocast_continue else R.string.autocast_open)
            if (Build.VERSION.SDK_INT >= 29) subtitle = getString(current.phase.messageResource())
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { openCast() } else openCast()
    }

    @Suppress("DEPRECATION")
    private fun openCast() {
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(AutoCastIntents.pendingIntent(this))
        else startActivityAndCollapse(AutoCastIntents.intent(this))
    }
}
