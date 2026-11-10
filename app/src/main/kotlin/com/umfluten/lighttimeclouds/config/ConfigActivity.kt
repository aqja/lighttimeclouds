package com.umfluten.lighttimeclouds.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.roundCoordinate
import com.umfluten.lighttimeclouds.geo.SharedLocation
import com.umfluten.lighttimeclouds.geo.ZoneSuggestion
import com.umfluten.lighttimeclouds.widget.ClockWidgetProvider
import com.umfluten.lighttimeclouds.widget.WidgetIds
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Launcher icon entry point and AppWidget "configure" screen - both routes land here, as does a
 * location shared from a map app, which then opens as a new place to check and save.
 */
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

        // After a rotation the place is offered again, but the note about a share without coordinates isn't repeated.
        val sharedPlace = sharedPlace(intent, noteIfNone = savedInstanceState == null)

        setContent {
            ConfigRoot(
                initialWidgetId = appWidgetId,
                // Tapping the widget can open the app too, with its id but as a plain visit.
                startOnSettings = intent?.action == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE || sharedPlace != null,
                sharedPlace = sharedPlace,
                onFinishedWithResult = { editedWidgetId -> finishWithResult(editedWidgetId) },
            )
        }
    }

    /**
     * A new, unsaved place from a shared `geo:` URI or text, with the time zone suggested from its
     * coordinates. Null for other intents; for a share without coordinates the app opens as usual,
     * with a note why no place was created.
     */
    private fun sharedPlace(intent: Intent?, noteIfNone: Boolean): Place? {
        val shared = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> return null
        }
        val location = shared?.let(SharedLocation::parse)
        if (location == null) {
            if (noteIfNone) Toast.makeText(this, R.string.share_no_coordinates, Toast.LENGTH_LONG).show()
            return null
        }
        val latitude = roundCoordinate(location.latitude)
        val longitude = roundCoordinate(location.longitude)
        return Place.new(
            name = location.name.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            zone = ZoneSuggestion.nearest(latitude, longitude) ?: ZoneId.systemDefault(),
        )
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
