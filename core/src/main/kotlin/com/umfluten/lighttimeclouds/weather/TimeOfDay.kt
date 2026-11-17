package com.umfluten.lighttimeclouds.weather

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The parts of a day the weather summary talks in, by local clock time. The night belongs to the
 * day whose evening it follows, so 1 am is still "tonight" rather than "today".
 */
enum class DayPeriod(val startHour: Int) {
    EARLY_MORNING(5),
    MORNING(9),
    MIDDAY(12),
    AFTERNOON(14),
    EVENING(18),
    NIGHT(22),
    ;

    companion object {
        fun ofHour(hour: Int): DayPeriod = entries.lastOrNull { hour >= it.startHour } ?: NIGHT
    }
}

/**
 * When something happens, only as precise as an hourly forecast deserves: a part of the day
 * rather than a clock time, which would claim more accuracy than the models have.
 */
sealed interface TimeOfDay {
    /** Within [SOON] from now. */
    data object Soon : TimeOfDay

    /** Later in the [period] it is now. */
    data class Later(val period: DayPeriod) : TimeOfDay

    /** In [period] of today or, if [tomorrow], of the next day. */
    data class InPeriod(val period: DayPeriod, val tomorrow: Boolean) : TimeOfDay

    companion object {
        val SOON: Duration = Duration.ofHours(2)

        /** [target] seen from [now], by the clock of [zone]. */
        fun of(target: Instant, now: Instant, zone: ZoneId): TimeOfDay {
            if (Duration.between(now, target) <= SOON) return Soon
            val period = DayPeriod.ofHour(target.atZone(zone).hour)
            val day = dayOf(target, zone)
            val today = dayOf(now, zone)
            return when {
                day == today && period == DayPeriod.ofHour(now.atZone(zone).hour) -> Later(period)
                else -> InPeriod(period, tomorrow = day.isAfter(today))
            }
        }

        /** The day [instant]'s period belongs to: the calendar day, but the previous one before dawn. */
        private fun dayOf(instant: Instant, zone: ZoneId): LocalDate =
            instant.atZone(zone).minusHours(DayPeriod.EARLY_MORNING.startHour.toLong()).toLocalDate()
    }
}
