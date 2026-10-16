package com.umfluten.lighttimeclouds.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetIds {
    /** Sentinel used where no concrete widget id applies (e.g. intents not tied to one instance). */
    const val NO_WIDGET_ID = AppWidgetManager.INVALID_APPWIDGET_ID

    fun activeWidgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
}
