package com.umfluten.lighttimeclouds.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the minute alarm after reboot, if any widget instance is currently placed. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && WidgetIds.activeWidgetIds(context).isNotEmpty()) {
            ClockAlarmScheduler.scheduleNextMinute(context)
        }
    }
}
