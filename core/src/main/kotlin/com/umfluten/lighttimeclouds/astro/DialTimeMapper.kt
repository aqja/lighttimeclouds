package com.umfluten.lighttimeclouds.astro

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Resolves an analog dial angle to the concrete real-world instant it should
 * represent *right now*.
 *
 * On a normal 12-hour clock face, every angle is shared by two real times
 * exactly 12h apart (e.g. the "3 o'clock" position is both 3:00 and 15:00).
 * A [DialTimeMapper] picks, for a given moment, an "active window" one turn of
 * the hour hand long ([dialHours]) and maps each angle to the one instant inside
 * that window with a matching hour-hand reading. On a 24-hour dial the window
 * decides where the shown day begins: at midnight, at another fixed time or
 * right at the current moment. The ring of sun/weather data drawn
 * around the dial is then simply "whichever window is currently active",
 * recomputed from scratch on every redraw (no explicit swap event needed).
 *
 * The hour hand shows wall-clock time, so all window arithmetic happens on the
 * wall clock and only the result becomes a real instant. Across a DST change a
 * window therefore lasts one hour more or less in real time.
 */
sealed interface DialTimeMapper {

    /** Hours per turn of the hour hand: 12 or 24. */
    val dialHours: Int

    /** Wall-clock start of the window that is active at the wall-clock time [now]; the window always contains [now]. */
    fun localWindowStart(now: LocalDateTime): LocalDateTime

    /** [angleDegrees] is 0..360, where `angleDegrees / 360 * dialHours` is the hour-hand reading. */
    fun resolve(now: ZonedDateTime, angleDegrees: Double): ZonedDateTime =
        resolveWithWindow(localWindowStart(now.toLocalDateTime()), angleDegrees, dialHours).toZoned(now)

    /** Start of the window that is active at [now]; the window always contains [now]. */
    fun windowStart(now: ZonedDateTime): ZonedDateTime = localWindowStart(now.toLocalDateTime()).toZoned(now)

    /** End of the window that is active at [now]: one turn of the hour hand after [windowStart]. */
    fun windowEnd(now: ZonedDateTime): ZonedDateTime =
        localWindowStart(now.toLocalDateTime()).plusHours(dialHours.toLong()).toZoned(now)

    /**
     * Fixed daily switch times define fixed windows: on a 12h dial two times (default 06:00/18:00),
     * on a 24h dial one (default midnight).
     */
    data class FixedTimes(val switchTimes: List<LocalTime>, override val dialHours: Int = 12) : DialTimeMapper {
        override fun localWindowStart(now: LocalDateTime): LocalDateTime =
            switchTimes.map { mostRecentOccurrence(now, it) }.max()
    }

    /**
     * The window boundary ("seam") lies at the current moment and slides
     * continuously forward with it, instead of flipping at fixed times.
     */
    data class Trailing(override val dialHours: Int = 12) : DialTimeMapper {
        override fun localWindowStart(now: LocalDateTime): LocalDateTime = now
    }

    companion object {
        private fun mostRecentOccurrence(now: LocalDateTime, timeOfDay: LocalTime): LocalDateTime {
            val candidate = now.toLocalDate().atTime(timeOfDay)
            return if (candidate.isAfter(now)) candidate.minusDays(1) else candidate
        }

        /**
         * The real instant of this wall-clock time in [now]'s zone. In the hour repeated when DST
         * ends, the pass with [now]'s offset wins, so the hour hand always points at "now". A time
         * skipped when DST starts becomes the moment of the jump, so the ring stays continuous
         * across the skipped hour.
         */
        private fun LocalDateTime.toZoned(now: ZonedDateTime): ZonedDateTime {
            val transition = now.zone.rules.getTransition(this)
            return if (transition != null && transition.isGap) {
                transition.instant.atZone(now.zone)
            } else {
                ZonedDateTime.ofLocal(this, now.zone, now.offset)
            }
        }

        /**
         * Within the unique window of [dialHours] starting at [windowStart], find the wall-clock time
         * whose hour-hand reading equals `angleDegrees / 360 * dialHours`. Works for any window
         * start because each hour-hand reading occurs exactly once per window.
         */
        internal fun resolveWithWindow(windowStart: LocalDateTime, angleDegrees: Double, dialHours: Int): LocalDateTime {
            val turnMinutes = dialHours * 60.0
            val targetMinutes = angleDegrees / 360.0 * turnMinutes
            val startMinuteOfDay = windowStart.hour * 60.0 + windowStart.minute + windowStart.second / 60.0
            val startMod = ((startMinuteOfDay % turnMinutes) + turnMinutes) % turnMinutes
            val offsetMinutes = (((targetMinutes - startMod) % turnMinutes) + turnMinutes) % turnMinutes
            return windowStart.plusSeconds((offsetMinutes * 60.0).toLong())
        }
    }
}
