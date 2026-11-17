package com.umfluten.lighttimeclouds.widget

import android.content.res.Resources
import androidx.annotation.ArrayRes
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.weather.PrecipitationKind
import com.umfluten.lighttimeclouds.weather.PrecipitationOutlook
import com.umfluten.lighttimeclouds.weather.Remark
import com.umfluten.lighttimeclouds.weather.TimeOfDay
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.weather.WeatherOutlook
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Puts the [WeatherOutlook.remarks] into words, one sentence each. One fixed wording per case, so
 * the widget's text does not change with every redraw while the weather stays the same; times
 * are parts of the day rather than clock times, as vague as an hourly forecast deserves.
 */
object WeatherSummaryText {

    /** The text for [forecast] at [now] (in the place's time zone), or null if the forecast does not cover it. */
    fun of(resources: Resources, forecast: WeatherForecast?, now: ZonedDateTime): String? {
        val outlook = forecast?.let { WeatherOutlook.of(it, now.toInstant(), now.zone) } ?: return null
        if (outlook.isEmpty) return null
        val locale = resources.configuration.locales[0]
        return outlook.remarks().joinToString(" ") { remark ->
            sentence(resources, remark).replaceFirstChar { it.titlecase(locale) }
        }
    }

    private fun sentence(resources: Resources, remark: Remark): String = when (remark) {
        is Remark.TemperatureChange -> temperatureSentence(resources, remark.degrees)
        is Remark.FeelsLike -> resources.getString(
            if (remark.offset < 0) R.string.summary_feels_colder else R.string.summary_feels_warmer,
            abs(remark.offset),
        )
        is Remark.Precipitation -> precipitationSentence(resources, remark.outlook)
    }

    private fun temperatureSentence(resources: Resources, change: Int): String = when {
        change >= WeatherOutlook.LARGE_CHANGE -> resources.getString(R.string.summary_much_warmer, change)
        change >= WeatherOutlook.NOTABLE_CHANGE -> resources.getString(R.string.summary_warmer)
        change <= -WeatherOutlook.LARGE_CHANGE -> resources.getString(R.string.summary_much_colder, -change)
        change <= -WeatherOutlook.NOTABLE_CHANGE -> resources.getString(R.string.summary_cooler)
        else -> resources.getString(R.string.summary_similar)
    }

    private fun precipitationSentence(resources: Resources, outlook: PrecipitationOutlook): String {
        val snow = when (outlook) {
            is PrecipitationOutlook.Dry -> false
            is PrecipitationOutlook.Ongoing -> outlook.kind == PrecipitationKind.SNOW
            is PrecipitationOutlook.Coming -> outlook.kind == PrecipitationKind.SNOW
        }
        return when (outlook) {
            is PrecipitationOutlook.Dry -> resources.getString(R.string.summary_dry, time(resources, outlook.until, Phrase.UNTIL))
            is PrecipitationOutlook.Ongoing -> when (val end = outlook.end) {
                null -> resources.getString(if (snow) R.string.summary_snow_lasts else R.string.summary_rain_lasts)
                else -> resources.getString(if (snow) R.string.summary_snow_stops else R.string.summary_rain_stops, time(resources, end, Phrase.AT))
            }
            is PrecipitationOutlook.Coming -> {
                val text = when {
                    snow && outlook.likely -> R.string.summary_snow_likely
                    snow -> R.string.summary_snow_possible
                    outlook.likely -> R.string.summary_rain_likely
                    else -> R.string.summary_rain_possible
                }
                resources.getString(text, time(resources, outlook.start, if (outlook.continues) Phrase.FROM else Phrase.AT))
            }
        }
    }

    /** How a part of the day is joined to the sentence: "this afternoon", "from this afternoon", "into the afternoon". */
    private enum class Phrase(@param:ArrayRes val today: Int, @param:ArrayRes val tomorrow: Int) {
        AT(R.array.summary_time_at_today, R.array.summary_time_at_tomorrow),
        FROM(R.array.summary_time_from_today, R.array.summary_time_from_tomorrow),
        UNTIL(R.array.summary_time_until_today, R.array.summary_time_until_tomorrow),
    }

    private fun time(resources: Resources, time: TimeOfDay, phrase: Phrase): String = when (time) {
        TimeOfDay.Soon -> resources.getString(R.string.summary_time_soon)
        is TimeOfDay.Later -> resources.getStringArray(R.array.summary_time_later)[time.period.ordinal]
        is TimeOfDay.InPeriod -> resources.getStringArray(if (time.tomorrow) phrase.tomorrow else phrase.today)[time.period.ordinal]
    }
}
