package com.umfluten.lighttimeclouds.widget

import android.content.res.Resources
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.weather.PrecipitationKind
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.weather.WeatherOutlook
import java.time.Instant
import kotlin.math.abs

/**
 * Puts a [WeatherOutlook] into words: up to three sentences on the temperature compared with
 * yesterday, the apparent temperature, and the precipitation to come. One fixed wording per case,
 * so the widget's text does not change with every redraw while the weather stays the same.
 */
object WeatherSummaryText {

    /** The text for [forecast] at [now], or null if the forecast does not cover it. */
    fun of(resources: Resources, forecast: WeatherForecast?, now: Instant): String? {
        val outlook = forecast?.let { WeatherOutlook.of(it, now) } ?: return null
        if (outlook.isEmpty) return null
        return listOfNotNull(
            outlook.changeSinceYesterday?.let { change -> temperatureSentence(resources, change) },
            outlook.feelsLikeOffset?.let { offset ->
                resources.getString(if (offset < 0) R.string.summary_feels_colder else R.string.summary_feels_warmer, abs(offset))
            },
            outlook.precipitation?.let { p ->
                val plural = when (p.kind) {
                    PrecipitationKind.SNOW -> R.plurals.summary_snow
                    PrecipitationKind.RAIN -> R.plurals.summary_rain
                    PrecipitationKind.CHANCE -> R.plurals.summary_chance
                    PrecipitationKind.DRY -> R.plurals.summary_dry
                }
                resources.getQuantityString(plural, p.hours, p.hours)
            },
        ).joinToString(" ")
    }

    private fun temperatureSentence(resources: Resources, change: Int): String = when {
        change >= WeatherOutlook.LARGE_CHANGE -> resources.getString(R.string.summary_much_warmer, change)
        change >= WeatherOutlook.NOTABLE_CHANGE -> resources.getString(R.string.summary_warmer)
        change <= -WeatherOutlook.LARGE_CHANGE -> resources.getString(R.string.summary_much_colder, -change)
        change <= -WeatherOutlook.NOTABLE_CHANGE -> resources.getString(R.string.summary_cooler)
        else -> resources.getString(R.string.summary_similar)
    }
}
