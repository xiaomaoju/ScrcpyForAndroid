package io.github.miuzarte.scrcpyforandroid.autocast

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import io.github.miuzarte.scrcpyforandroid.R
import io.github.miuzarte.scrcpyforandroid.services.AppRuntime

class AutoCastWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = AppRuntime.autoCast?.state?.value ?: AutoCastState()
        appWidgetIds.forEach { update(context, appWidgetManager, it, state) }
    }

    companion object {
        internal fun refresh(context: Context, state: AutoCastState) {
            android.service.quicksettings.TileService.requestListeningState(context,
                ComponentName(context, AutoCastTileService::class.java))
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, AutoCastWidgetProvider::class.java)).forEach {
                update(context, manager, it, state)
            }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int, state: AutoCastState) {
            val views = RemoteViews(context.packageName, R.layout.autocast_widget)
            val title = when {
                state.phase == AutoCastPhase.RUNNING -> R.string.autocast_continue
                state.phase == AutoCastPhase.NEEDS_PAIRING -> R.string.autocast_repair
                state.busy -> R.string.autocast_opening
                else -> R.string.autocast_open
            }
            views.setTextViewText(R.id.autocast_widget_title, context.getString(title))
            views.setOnClickPendingIntent(R.id.autocast_widget_root, AutoCastIntents.pendingIntent(context))
            manager.updateAppWidget(id, views)
        }
    }
}
