package com.umfluten.lighttimeclouds.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/**
 * Minute-accurate redraw scheduling. [Intent.ACTION_TIME_TICK] cannot be
 * manifest-registered and WorkManager floors at 15 minutes, so we re-arm an
 * inexact [AlarmManager] alarm to the next minute boundary after every tick.
 * A non-ticking analog minute hand tolerates a few seconds of drift, so no
 * exact-alarm permission is needed.
 */
object ClockAlarmScheduler {
    private const val REQUEST_CODE = 1001
    private const val ACTION_TICK = "com.umfluten.lighttimeclouds.action.TICK"

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockUpdateReceiver::class.java).apply { action = ACTION_TICK }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun scheduleNextMinute(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = ZonedDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, next.toInstant().toEpochMilli(), pendingIntent(context))
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }
}
