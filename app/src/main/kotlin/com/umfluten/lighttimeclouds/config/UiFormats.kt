package com.umfluten.lighttimeclouds.config

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/**
 * Date, time and coordinate formats of the config UI, following [locale] and the device's 12/24-hour
 * setting. Uses ICU skeletons, so each locale gets its own field order and separators.
 */
internal class UiFormats(val locale: Locale, val is24Hour: Boolean) {
    private val timeOfDayFormat = DateFormat.getInstanceForSkeleton(if (is24Hour) "Hm" else "hm", locale)
    private val timeFormat = DateFormat.getInstanceForSkeleton(if (is24Hour) "Hmz" else "hmz", locale)
    private val dateFormat = DateFormat.getInstanceForSkeleton("EEEMMMd", locale)

    /** Time with zone abbreviation, e.g. "07:42 MESZ" or "7:42 AM GMT+2". */
    fun time(dateTime: ZonedDateTime): String = format(timeFormat, dateTime)

    /** Short weekday and date, e.g. "Sa., 18. Okt." or "Sat, Oct 18". */
    fun date(dateTime: ZonedDateTime): String = format(dateFormat, dateTime)

    /** A time of day given in minutes since midnight, without zone. */
    fun timeOfDay(minutesOfDay: Int): String {
        val h = (minutesOfDay / 60).coerceIn(0, 23)
        val m = (minutesOfDay % 60).coerceIn(0, 59)
        return format(timeOfDayFormat, LocalDate.of(2000, 1, 1).atTime(h, m).atZone(ZoneId.of("UTC")))
    }

    fun coordinates(latitude: Double, longitude: Double): String =
        String.format(locale, "%.4f°, %.4f°", latitude, longitude)

    private fun format(format: DateFormat, dateTime: ZonedDateTime): String {
        format.timeZone = TimeZone.getTimeZone(dateTime.zone.id)
        return format.format(Date.from(dateTime.toInstant()))
    }
}

@Composable
internal fun rememberUiFormats(): UiFormats {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { UiFormats(locale, android.text.format.DateFormat.is24HourFormat(context)) }
}
