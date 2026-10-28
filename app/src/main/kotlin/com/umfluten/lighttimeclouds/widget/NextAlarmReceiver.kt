package com.umfluten.lighttimeclouds.widget

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Redraws every placed widget as soon as the next alarm clock changes (set, deleted, snoozed, dismissed),
 * instead of waiting for the next minute tick. Fires a few times a day; widgets without the alarm tick
 * are redrawn too, for simplicity.
 */
class NextAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ClockWidgetProvider.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
