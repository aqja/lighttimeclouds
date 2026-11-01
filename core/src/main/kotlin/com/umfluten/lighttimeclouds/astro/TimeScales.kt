package com.umfluten.lighttimeclouds.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Time scales for the ephemerides. [Instant]s count UTC without leap seconds. Terrestrial Time,
 * the argument of the theories, is TT = TAI + 32.184 s = UTC + 32.184 s + (TAI − UTC) with the
 * leap seconds announced in IERS Bulletin C; none after 2017 so far. The Earth's rotation angle
 * needs UT1, taken as UTC: by definition they differ by less than 0.9 s, which turns the sky by
 * at most 14″.
 */
internal object TimeScales {
    const val J2000 = 2451545.0
    const val DAYS_PER_CENTURY = 36525.0
    private const val UNIX_EPOCH_JD = 2440587.5
    private const val SECONDS_PER_DAY = 86400.0
    private const val TT_MINUS_TAI = 32.184

    /** First day of each TAI − UTC value (seconds), from 1972 on. */
    private val LEAP_SECONDS: List<Pair<Long, Int>> = listOf(
        "1972-01-01" to 10, "1972-07-01" to 11, "1973-01-01" to 12, "1974-01-01" to 13, "1975-01-01" to 14,
        "1976-01-01" to 15, "1977-01-01" to 16, "1978-01-01" to 17, "1979-01-01" to 18, "1980-01-01" to 19,
        "1981-07-01" to 20, "1982-07-01" to 21, "1983-07-01" to 22, "1985-07-01" to 23, "1988-01-01" to 24,
        "1990-01-01" to 25, "1991-01-01" to 26, "1992-07-01" to 27, "1993-07-01" to 28, "1994-07-01" to 29,
        "1996-01-01" to 30, "1997-07-01" to 31, "1999-01-01" to 32, "2006-01-01" to 33, "2009-01-01" to 34,
        "2012-07-01" to 35, "2015-07-01" to 36, "2017-01-01" to 37,
    ).map { (date, seconds) -> LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toEpochSecond() to seconds }

    /** Julian date of [instant] in UT1 (≈ UTC). */
    fun julianDateUt1(instant: Instant): Double = UNIX_EPOCH_JD + (instant.epochSecond + instant.nano / 1e9) / SECONDS_PER_DAY

    /** Julian centuries of Terrestrial Time since J2000 (TT, 2000-01-01 12:00). */
    fun centuriesTt(instant: Instant): Double =
        (julianDateUt1(instant) + ttMinusUtcSeconds(instant) / SECONDS_PER_DAY - J2000) / DAYS_PER_CENTURY

    private fun ttMinusUtcSeconds(instant: Instant): Double {
        val taiMinusUtc = LEAP_SECONDS.lastOrNull { instant.epochSecond >= it.first }?.second ?: LEAP_SECONDS.first().second
        return TT_MINUS_TAI + taiMinusUtc
    }
}
