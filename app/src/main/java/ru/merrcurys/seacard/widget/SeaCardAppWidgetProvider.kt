package ru.merrcurys.seacard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
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
        return try {
            ColorUtils.blendARGB(Color.GRAY, Color.WHITE, 0.7f)
            val systemTheme = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
            val attrs = intArrayOf(
                android.R.attr.colorPrimary,
                android.R.attr.colorAccent
            )
            val ta = systemTheme.theme.obtainStyledAttributes(attrs)
            val primary = ta.getColor(0, Color.GRAY).takeIf { it != 0 } ?: ta.getColor(1, Color.GRAY)
            ta.recycle()

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(primary, hsl)
            // Ограничиваем в допустимый диапазон (защита от «битых» тем)
            hsl[0] = hsl[0].coerceIn(0f, 360f)
            hsl[1] = hsl[1].coerceIn(0f, 1f)
            hsl[2] = hsl[2].coerceIn(0f, 1f)
            // Светлый фон: поднимаем насыщенность и яркость
            hsl[1] = (hsl[1] * 2f).coerceIn(0f, 1f)
            hsl[2] = 0.20f  // осветляем
            ColorUtils.HSLToColor(hsl)
        } catch (e: Exception) {
            // Запасной цвет при любой ошибке (светло-серый)
            ColorUtils.blendARGB(Color.GRAY, Color.BLACK, 0.6f)
        }
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
