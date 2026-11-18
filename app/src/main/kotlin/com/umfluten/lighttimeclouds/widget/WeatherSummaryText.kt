package com.umfluten.lighttimeclouds.widget

import android.content.res.Resources
import androidx.annotation.ArrayRes
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.weather.CloudOutlook
import com.umfluten.lighttimeclouds.weather.HeavyRainWarning
import com.umfluten.lighttimeclouds.weather.PrecipitationKind
import com.umfluten.lighttimeclouds.weather.PrecipitationOutlook
import com.umfluten.lighttimeclouds.weather.Remark
import com.umfluten.lighttimeclouds.weather.SpellCharacter
import com.umfluten.lighttimeclouds.weather.TemperatureCourse
import com.umfluten.lighttimeclouds.weather.TomorrowOutlook
import com.umfluten.lighttimeclouds.weather.TimeOfDay
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.weather.WeatherOutlook
import com.umfluten.lighttimeclouds.weather.WeatherWarning
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
        is Remark.Snow -> resources.getStringArray(R.array.summary_fresh_snow)[remark.amount.ordinal]
        is Remark.Temperature -> courseSentence(resources, remark.course)
        is Remark.Warning -> when (val w = remark.warning) {
            is WeatherWarning.HeavyRain -> resources.getString(
                when (w.level) {
                    HeavyRainWarning.MARKED -> R.string.summary_heavy_rain
                    HeavyRainWarning.SEVERE -> R.string.summary_heavy_rain_severe
                    HeavyRainWarning.EXTREME -> R.string.summary_heavy_rain_extreme
                },
                time(resources, w.at, Phrase.AT),
            )
            is WeatherWarning.FreezingRain -> resources.getString(R.string.summary_freezing_rain, time(resources, w.at, Phrase.AT))
        }
        is Remark.Tomorrow -> tomorrowSentence(resources, remark.outlook)
        is Remark.Wind -> resources.getString(
            R.string.summary_possible_plural,
            time(resources, remark.at, Phrase.AT),
            resources.getStringArray(R.array.summary_gusts)[remark.level.ordinal],
        )
        is Remark.Clouds -> when (val c = remark.clouds) {
            is CloudOutlook.Clearing -> resources.getString(R.string.summary_clearing, time(resources, c.start, Phrase.AT))
            is CloudOutlook.CloudingOver -> resources.getString(R.string.summary_clouding_over, time(resources, c.start, Phrase.AT))
            is CloudOutlook.Overcast -> resources.getString(R.string.summary_overcast, time(resources, c.until, Phrase.UNTIL))
            is CloudOutlook.Clear -> resources.getString(R.string.summary_clear, time(resources, c.until, Phrase.UNTIL))
        }
    }

    private fun courseSentence(resources: Resources, course: TemperatureCourse): String = when (course) {
        is TemperatureCourse.Heat -> when (course.at) {
            // Hot already or within the next hours.
            TimeOfDay.Soon -> resources.getString(R.string.summary_heat_now, degrees(course.maxC))
            else -> resources.getString(R.string.summary_heat, time(resources, course.at, Phrase.AT), degrees(course.maxC))
        }
        is TemperatureCourse.Frost -> resources.getString(R.string.summary_frost, time(resources, course.at, Phrase.AT), degrees(course.minC))
        is TemperatureCourse.LastingFrost ->
            resources.getString(R.string.summary_lasting_frost, time(resources, course.until, Phrase.UNTIL), degrees(course.maxC))
        is TemperatureCourse.Warming -> resources.getString(R.string.summary_warming, time(resources, course.until, Phrase.UNTIL), course.degrees)
        is TemperatureCourse.Cooling -> resources.getString(R.string.summary_cooling, time(resources, course.until, Phrase.UNTIL), course.degrees)
    }

    private fun tomorrowSentence(resources: Resources, t: TomorrowOutlook): String {
        val temperatures = if (t.todayMaxC < COLD_DAY_C) R.array.summary_tomorrow_temperature_cold else R.array.summary_tomorrow_temperature
        val temperature = when {
            t.change <= -WeatherOutlook.LARGE_CHANGE -> 0
            t.change <= -WeatherOutlook.NOTABLE_CHANGE -> 1
            t.change < WeatherOutlook.NOTABLE_CHANGE -> 2
            t.change < WeatherOutlook.LARGE_CHANGE -> 3
            else -> 4
        }
        val precipitations = if (t.kind == PrecipitationKind.SNOW) R.array.summary_tomorrow_snow else R.array.summary_tomorrow_rain
        return resources.getString(
            R.string.summary_tomorrow,
            resources.getStringArray(temperatures)[temperature],
            resources.getStringArray(precipitations)[t.precipitation.ordinal],
        )
    }

    /** Below this highest temperature (°C), "colder" and "milder" fit better than "cooler" and "warmer". */
    private const val COLD_DAY_C = 10

    private fun degrees(celsius: Int): String = formatTemperature(celsius.toDouble())

    private fun temperatureSentence(resources: Resources, change: Int): String = when {
        change >= WeatherOutlook.LARGE_CHANGE -> resources.getString(R.string.summary_much_warmer, change)
        change >= WeatherOutlook.NOTABLE_CHANGE -> resources.getString(R.string.summary_warmer)
        change <= -WeatherOutlook.LARGE_CHANGE -> resources.getString(R.string.summary_much_colder, -change)
        change <= -WeatherOutlook.NOTABLE_CHANGE -> resources.getString(R.string.summary_cooler)
        else -> resources.getString(R.string.summary_similar)
    }

    private fun precipitationSentence(resources: Resources, outlook: PrecipitationOutlook): String = when (outlook) {
        is PrecipitationOutlook.Dry -> resources.getString(R.string.summary_dry, time(resources, outlook.until, Phrase.UNTIL))
        is PrecipitationOutlook.Ongoing -> {
            val what = what(resources, outlook, definite = true)
            val plural = outlook.character == SpellCharacter.SHOWERS
            when (val end = outlook.end) {
                null -> resources.getString(if (plural) R.string.summary_lasts_plural else R.string.summary_lasts, what)
                else -> resources.getString(
                    if (plural) R.string.summary_stops_plural else R.string.summary_stops,
                    what,
                    time(resources, end, Phrase.AT),
                )
            }
        }
        is PrecipitationOutlook.Coming -> resources.getString(
            when {
                outlook.character == SpellCharacter.SHOWERS -> if (outlook.likely) R.string.summary_likely_plural else R.string.summary_possible_plural
                else -> if (outlook.likely) R.string.summary_likely else R.string.summary_possible
            },
            time(resources, outlook.start, if (outlook.continues) Phrase.FROM else Phrase.AT),
            what(resources, outlook, definite = false),
        )
    }

    /** "light showers", or with [definite] "the light showers". */
    private fun what(resources: Resources, spell: PrecipitationOutlook.Spell, definite: Boolean): String {
        val arrays = NOUNS.getValue(spell.kind to spell.character)
        return resources.getStringArray(if (definite) arrays.second else arrays.first)[spell.intensity.ordinal]
    }

    /** Per kind and character the noun arrays without and with the definite article, each by intensity. */
    private val NOUNS = mapOf(
        (PrecipitationKind.RAIN to SpellCharacter.NORMAL) to (R.array.summary_rain to R.array.summary_rain_definite),
        (PrecipitationKind.RAIN to SpellCharacter.SHOWERS) to (R.array.summary_rain_showers to R.array.summary_rain_showers_definite),
        (PrecipitationKind.RAIN to SpellCharacter.STEADY) to (R.array.summary_rain_steady to R.array.summary_rain_steady_definite),
        (PrecipitationKind.SNOW to SpellCharacter.NORMAL) to (R.array.summary_snow to R.array.summary_snow_definite),
        (PrecipitationKind.SNOW to SpellCharacter.SHOWERS) to (R.array.summary_snow_showers to R.array.summary_snow_showers_definite),
        (PrecipitationKind.SNOW to SpellCharacter.STEADY) to (R.array.summary_snow_steady to R.array.summary_snow_steady_definite),
    )

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
