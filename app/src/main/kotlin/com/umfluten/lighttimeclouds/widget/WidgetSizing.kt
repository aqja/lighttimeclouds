package com.umfluten.lighttimeclouds.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.util.Size
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

    /**
     * Size of the whole widget as shown now: launchers report the width in portrait as the minimum
     * and the height as the maximum, and the other way round in landscape.
     */
    fun currentWidgetSizePx(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Size {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        fun dp(key: String) = options.getInt(key, 0)
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        val minWidth = dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 110
        val minHeight = dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 110
        val width = if (portrait) minWidth else dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).coerceAtLeast(minWidth)
        val height = if (portrait) dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).coerceAtLeast(minHeight) else minHeight
        return Size(dpToPx(context, width), dpToPx(context, height))
    }

    private fun dpToPx(context: Context, dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), context.resources.displayMetrics).toInt()
}
