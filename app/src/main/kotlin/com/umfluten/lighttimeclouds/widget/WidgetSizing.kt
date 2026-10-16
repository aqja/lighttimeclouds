package com.umfluten.lighttimeclouds.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.TypedValue

/** Derives the square pixel size to render the clock bitmap at, from the widget's current granted size. */
object WidgetSizing {
    fun currentSizePx(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Int {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        val sizeDp = minOf(minWidthDp, minHeightDp).coerceAtLeast(40)
        val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeDp.toFloat(), context.resources.displayMetrics)
        return sizePx.toInt().coerceAtLeast(64)
    }
}
