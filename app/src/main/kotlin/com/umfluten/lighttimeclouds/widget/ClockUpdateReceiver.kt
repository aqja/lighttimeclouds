package com.umfluten.lighttimeclouds.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired once a minute by [ClockAlarmScheduler]; redraws every placed widget and re-arms the next tick. */
class ClockUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ClockWidgetProvider.updateAll(context)
            } finally {
                ClockAlarmScheduler.scheduleNextMinute(context)
                pending.finish()
            }
        }
    }
}
