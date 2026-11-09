package com.umfluten.lighttimeclouds.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.util.SizeF
import android.util.TypedValue
import androidx.core.os.BundleCompat

/** Derives pixel sizes for the widget's content from its current granted size. */
object WidgetSizing {
    /**
     * Square size to render the clock bitmap at: [clockSideDp] in pixels, at most the screen's
     * shorter side.
     */
    fun currentSizePx(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int): Int {
        val sideDp = clockSideDp(appWidgetManager.getAppWidgetOptions(appWidgetId))
        val screen = context.resources.displayMetrics.let { minOf(it.widthPixels, it.heightPixels) }
        return dpToPx(context, sideDp).coerceIn(64, screen.coerceAtLeast(64))
    }

    /**
     * Side (dp) of the largest square that fits the widget in any of the sizes it is laid out in
     * ([reportedSizesDp]): the clock stays sharp in portrait and in landscape, and the image view
     * scales it down where there is less room.
     */
    internal fun clockSideDp(options: Bundle): Float =
        reportedSizesDp(options).maxOf { minOf(it.width, it.height) }.coerceAtLeast(40f)

    /**
     * The sizes (dp) the launcher lays the widget out in. From Android 12 on it reports each of
     * them; before, only bounds, where the width in portrait is the minimum width and the height
     * the maximum height, and the other way round in landscape. The minimum height alone is thus
     * the landscape height, far smaller than the portrait height on a phone. Those two come in the
     * order portrait, landscape.
     */
    internal fun reportedSizesDp(options: Bundle): List<SizeF> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = BundleCompat.getParcelableArrayList(options, AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
            if (!sizes.isNullOrEmpty()) return sizes
        }
        fun dp(key: String) = options.getInt(key, 0).toFloat()
        val minWidth = dp(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: DEFAULT_SIZE_DP
        val minHeight = dp(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: DEFAULT_SIZE_DP
        val maxWidth = dp(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).coerceAtLeast(minWidth)
        val maxHeight = dp(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).coerceAtLeast(minHeight)
        return listOf(SizeF(minWidth, maxHeight), SizeF(maxWidth, minHeight))
    }

    /** [sizeDp] in pixels. */
    fun sizePx(context: Context, sizeDp: SizeF): Size =
        Size(dpToPx(context, sizeDp.width), dpToPx(context, sizeDp.height))

    /** The widget's declared default size (dp), for when the launcher reports none. */
    private const val DEFAULT_SIZE_DP = 110f

    private fun dpToPx(context: Context, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt()
}
