package com.umfluten.lighttimeclouds.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.TypedValue

/** Derives pixel sizes for the widget's content from its current granted size. */
object WidgetSizing {
    /** Square size to render the clock bitmap at. */
    fun currentSizePx(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Int {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val sizeDp = minOf(minWidthDp, minHeightDp).coerceAtLeast(40)
        return dpToPx(context, sizeDp).coerceAtLeast(64)
    }

    /** Width of the whole widget. */
    fun currentWidthPx(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Int =
        dpToPx(context, appWidgetManager.getAppWidgetOptions(appWidgetId).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110))

    private fun dpToPx(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
