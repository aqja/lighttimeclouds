package com.umfluten.lighttimeclouds.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.umfluten.lighttimeclouds.widget.ClockWidgetProvider
import com.umfluten.lighttimeclouds.widget.WidgetIds
import kotlinx.coroutines.launch

/** Launcher icon entry point and AppWidget "configure" screen - both routes land here. */
class ConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = WidgetIds.NO_WIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        // The activity is exported (launcher, configure action), so only accept ids of our own widgets.
        val requestedId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, WidgetIds.NO_WIDGET_ID)
            ?: WidgetIds.NO_WIDGET_ID
        appWidgetId = requestedId.takeIf { it in WidgetIds.activeWidgetIds(this) } ?: WidgetIds.NO_WIDGET_ID

        setContent {
            ConfigRoot(
                initialWidgetId = appWidgetId,
                onFinishedWithResult = { editedWidgetId -> finishWithResult(editedWidgetId) },
            )
        }
    }

    private fun finishWithResult(editedWidgetId: Int) {
        if (appWidgetId != WidgetIds.NO_WIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        }
        if (editedWidgetId != WidgetIds.NO_WIDGET_ID) {
            lifecycleScope.launch {
                val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                ClockWidgetProvider.updateWidget(applicationContext, appWidgetManager, editedWidgetId)
                finish()
            }
        } else {
            finish()
        }
    }

    companion object {
        fun intentFor(context: Context, widgetId: Int): Intent =
            Intent(context, ConfigActivity::class.java).apply {
                if (widgetId != WidgetIds.NO_WIDGET_ID) {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
            }
    }
}
