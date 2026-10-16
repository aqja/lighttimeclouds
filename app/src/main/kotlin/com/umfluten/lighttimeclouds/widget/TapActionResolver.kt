package com.umfluten.lighttimeclouds.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.umfluten.lighttimeclouds.config.ConfigActivity
import com.umfluten.lighttimeclouds.data.TapAction

/** Builds the PendingIntent fired when the widget is tapped, per [TapAction] setting. */
object TapActionResolver {

    fun buildPendingIntent(context: Context, widgetId: Int, tapAction: TapAction): PendingIntent {
        val intent = when (tapAction) {
            TapAction.OWN_APP -> ConfigActivity.intentFor(context, widgetId)
            TapAction.SYSTEM_CLOCK -> systemClockIntent(context)
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * [AlarmClock.ACTION_SHOW_ALARMS] is implemented by essentially every stock
     * and OEM clock app, so it is used to find the clock app. Firing it directly
     * would require the SET_ALARM permission, so we launch that app's regular
     * launcher entry instead. If nothing resolves (rare, some heavily reskinned
     * ROMs), fall back to our own config screen instead of a dead tap target.
     */
    private fun systemClockIntent(context: Context): Intent {
        val packageManager = context.packageManager
        val clockPackage = Intent(AlarmClock.ACTION_SHOW_ALARMS)
            .resolveActivity(packageManager)
            ?.packageName
        return clockPackage?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: ConfigActivity.intentFor(context, WidgetIds.NO_WIDGET_ID)
    }
}
