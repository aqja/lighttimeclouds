package com.umfluten.lighttimeclouds.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.config.ConfigActivity
import com.umfluten.lighttimeclouds.data.TapAction
import com.umfluten.lighttimeclouds.data.WidgetSettings
import kotlin.math.cos
import kotlin.math.sin

/** Makes the widget react to taps per [TapAction] setting. */
object TapActionResolver {

    /**
     * Whether [TapAction.SPLIT] works here: its tap areas are sized at runtime, which widgets only
     * allow from Android 12 on. Before, it acts like [TapAction.SYSTEM_CLOCK].
     */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val splitAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** Binds the taps of widget [widgetId], whose clock is shown [clockSidePx] wide. */
    fun bind(context: Context, views: RemoteViews, widgetId: Int, settings: WidgetSettings, clockSidePx: Int) {
        val rootOpensClock = when (settings.tapAction) {
            TapAction.OWN_APP -> false
            TapAction.SYSTEM_CLOCK -> true
            TapAction.SPLIT -> !splitAvailable
        }
        views.setOnClickPendingIntent(
            R.id.widget_root,
            if (rootOpensClock) clockPendingIntent(context, widgetId) else ownAppPendingIntent(context, widgetId),
        )
        if (settings.tapAction == TapAction.SPLIT && splitAvailable) {
            val ringRadius = dialRadius(clockSidePx, settings) * innerRingEdge(settings)
            bindTapAreas(views, clockPendingIntent(context, widgetId), ringRadius)
        } else {
            // Launchers apply an update to the views they already have, so areas shown before stay until hidden.
            TAP_AREAS.forEach { views.setViewVisibility(it, View.GONE) }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun bindTapAreas(views: RemoteViews, opens: PendingIntent, ringRadius: Float) {
        for ((viewId, size) in TAP_AREAS.zip(tapAreaSizes(ringRadius))) {
            views.setViewVisibility(viewId, View.VISIBLE)
            views.setViewLayoutWidth(viewId, size.width, TypedValue.COMPLEX_UNIT_PX)
            views.setViewLayoutHeight(viewId, size.height, TypedValue.COMPLEX_UNIT_PX)
            views.setOnClickPendingIntent(viewId, opens)
        }
    }

    /**
     * Sizes (px) of the centered rectangles that together trace a circle of [radius], in the order
     * of [TAP_AREAS]. Their corners lie on the circle, so that a tap on the ring never counts as
     * inside it; the middle is easy to hit, so the notches between the corners matter less.
     */
    internal fun tapAreaSizes(radius: Float): List<SizeF> = TAP_AREA_ANGLES.map { degrees ->
        val angle = Math.toRadians(degrees)
        SizeF(2 * radius * cos(angle).toFloat(), 2 * radius * sin(angle).toFloat())
    }

    private val TAP_AREAS = listOf(R.id.widget_tap_area_wide, R.id.widget_tap_area_square, R.id.widget_tap_area_tall)

    /** Direction of each tap area's corner, from the horizontal. */
    private val TAP_AREA_ANGLES = listOf(18.0, 45.0, 72.0)

    /** The two kinds of target need request codes of their own: without a clock app they are the same intent. */
    private fun ownAppPendingIntent(context: Context, widgetId: Int): PendingIntent =
        pendingIntent(context, 2 * widgetId, ConfigActivity.intentFor(context, widgetId))

    private fun clockPendingIntent(context: Context, widgetId: Int): PendingIntent =
        pendingIntent(context, 2 * widgetId + 1, systemClockIntent(context))

    private fun pendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

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
