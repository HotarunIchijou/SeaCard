package ru.merrcurys.seacard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.widget.RemoteViews
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.features.detail.CardDetailActivity

class SeaCardAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun getSystemAccentColor(context: Context): Int {
        val systemTheme = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
        // Пробуем colorPrimary (основной цвет системы), затем colorAccent
        val attrs = intArrayOf(
            android.R.attr.colorPrimary,
            android.R.attr.colorAccent
        )
        val ta = systemTheme.theme.obtainStyledAttributes(attrs)
        val color = ta.getColor(0, Color.GRAY).takeIf { it != 0 } ?: ta.getColor(1, Color.GRAY)
        ta.recycle()
        return color
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_seacard)
        views.setInt(R.id.widget_root, "setBackgroundColor", getSystemAccentColor(context))
        val serviceIntent = Intent(context, SeaCardWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setRemoteAdapter(R.id.widget_cards_grid, serviceIntent)

        val templateIntent = Intent(context, CardDetailActivity::class.java)
        views.setPendingIntentTemplate(
            R.id.widget_cards_grid,
            PendingIntent.getActivity(context, 0, templateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_cards_grid)
    }
}
