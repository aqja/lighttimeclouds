package com.umfluten.lighttimeclouds.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.LabelPosition
import com.umfluten.lighttimeclouds.data.SummaryPosition
import com.umfluten.lighttimeclouds.data.WeatherRepository
import com.umfluten.lighttimeclouds.data.WidgetPrefsRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant

class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
                // A newly placed widget may show weather via the template; a recent cache is reused.
                WeatherSync.update(context, refreshNow = true)
            } finally {
                pending.finish()
            }
        }
        ClockAlarmScheduler.scheduleNextMinute(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateWidget(context, appWidgetManager, appWidgetId)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onEnabled(context: Context) {
        ClockAlarmScheduler.scheduleNextMinute(context)
    }

    override fun onDisabled(context: Context) {
        ClockAlarmScheduler.cancel(context)
        WeatherSync.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pending = goAsync()
        val repo = WidgetPrefsRepository(context)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                appWidgetIds.forEach { repo.delete(it) }
                // Stops the hourly forecast job once no remaining widget shows weather.
                WeatherSync.update(context, refreshNow = false)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Shows the place name above or below the clock per [WidgetSettings.labelPosition], in the dial's chrome color. */
        private fun bindPlaceLabel(context: Context, views: RemoteViews, settings: WidgetSettings) {
            val color = ClockFaceRenderer.chromeColor(context, settings)
            for ((viewId, position) in listOf(R.id.widget_label_above to LabelPosition.ABOVE, R.id.widget_label_below to LabelPosition.BELOW)) {
                val visible = settings.labelPosition == position
                views.setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
                if (visible) {
                    views.setTextViewText(viewId, settings.place.name)
                    views.setTextColor(viewId, color)
                }
            }
        }

        /**
         * Shows the weather summary left of, right of or below the clock per
         * [WidgetSettings.summaryPosition] (only with the weather ring), in the dial's chrome color.
         * Stays hidden while the forecast does not cover the current time.
         */
        private fun bindSummary(context: Context, views: RemoteViews, settings: WidgetSettings, weather: WeatherForecast?, widthPx: Int) {
            val text = if (settings.showWeather && settings.summaryPosition != SummaryPosition.NONE) {
                WeatherSummaryText.of(context.resources, weather, Instant.now())
            } else {
                null
            }
            val color = ClockFaceRenderer.chromeColor(context, settings)
            for ((viewId, position) in listOf(
                R.id.widget_summary_left to SummaryPosition.LEFT,
                R.id.widget_summary_right to SummaryPosition.RIGHT,
                R.id.widget_summary_below to SummaryPosition.BELOW,
            )) {
                val visible = text != null && settings.summaryPosition == position
                views.setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
                if (visible) {
                    views.setTextViewText(viewId, text)
                    views.setTextColor(viewId, color)
                    if (position != SummaryPosition.BELOW) {
                        views.setInt(viewId, "setMaxWidth", (widthPx * MAX_SIDE_SUMMARY_WIDTH).toInt())
                    }
                }
            }
        }

        /** Share of the widget's width that text beside the clock may take; the clock shrinks to make room. */
        private const val MAX_SIDE_SUMMARY_WIDTH = 0.4f

        /** Redraws every placed widget, e.g. on the minute tick or after a shared place was edited. */
        suspend fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            WidgetIds.activeWidgetIds(context).forEach { id -> updateWidget(context, appWidgetManager, id) }
        }

        /**
         * The next alarm clock set by any app (e.g. the system clock); Android exposes only this one,
         * not the full list of alarms. Needs no permission.
         */
        fun nextAlarm(context: Context): Instant? =
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock
                ?.triggerTime?.let(Instant::ofEpochMilli)

        /** Settings [appWidgetId] is drawn with: its own, or the template while it has never been saved. */
        suspend fun effectiveSettings(context: Context, appWidgetId: Int): WidgetSettings {
            val repo = WidgetPrefsRepository(context)
            if (!repo.isConfigured(appWidgetId) && repo.isConfigured(WidgetSettings.DEFAULT_WIDGET_ID)) {
                return repo.load(WidgetSettings.DEFAULT_WIDGET_ID).copy(widgetId = appWidgetId)
            }
            return repo.load(appWidgetId)
        }

        /** Renders and pushes one widget; a freshly placed, never-saved widget uses the template settings. */
        suspend fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val settings = effectiveSettings(context, appWidgetId)
            val weather = if (settings.showWeather) WeatherRepository(context).cached(settings.place) else null

            val sizePx = WidgetSizing.currentSizePx(context, appWidgetManager, appWidgetId)
            val nextAlarm = if (settings.showNextAlarm) nextAlarm(context) else null
            val bitmap = ClockFaceRenderer.render(context, sizePx, settings, weather, nextAlarm = nextAlarm)

            val views = RemoteViews(context.packageName, R.layout.widget_clock)
            views.setImageViewBitmap(R.id.widget_clock_image, bitmap)
            bindPlaceLabel(context, views, settings)
            bindSummary(context, views, settings, weather, WidgetSizing.currentWidthPx(context, appWidgetManager, appWidgetId))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                TapActionResolver.buildPendingIntent(context, appWidgetId, settings.tapAction),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
