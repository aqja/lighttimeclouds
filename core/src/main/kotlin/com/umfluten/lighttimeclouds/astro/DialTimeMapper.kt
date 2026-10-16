package com.umfluten.lighttimeclouds.astro

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Resolves a 12-hour analog dial angle to the concrete real-world instant it
 * should represent *right now*.
 *
 * On a normal 12-hour clock face, every angle is shared by two real times
 * exactly 12h apart (e.g. the "3 o'clock" position is both 3:00 and 15:00).
 * A [DialTimeMapper] picks, for a given moment, a 12-hour "active window" and
 * maps each angle to the one instant inside that window with a matching
 * hour-hand reading. The ring of sun/weather data drawn around the dial is
 * then simply "whichever 12h window is currently active", recomputed from
 * scratch on every redraw (no explicit swap event needed).
 */
sealed interface DialTimeMapper {

    /** [angleDegrees] is 0..360, where `angleDegrees / 30` is the hour-hand reading (0..12). */
    fun resolve(now: ZonedDateTime, angleDegrees: Double): ZonedDateTime =
        resolveWithWindow(windowStart(now), angleDegrees)

    /** Start of the 12h window that is active at [now]; the window always contains [now]. */
    fun windowStart(now: ZonedDateTime): ZonedDateTime

    /** Two configurable fixed daily switch times (default 06:00/18:00) define two fixed 12h windows. */
    data class FixedTimes(val switchTime1: LocalTime, val switchTime2: LocalTime) : DialTimeMapper {
        override fun windowStart(now: ZonedDateTime): ZonedDateTime {
            val c1 = mostRecentOccurrence(now, switchTime1)
            val c2 = mostRecentOccurrence(now, switchTime2)
            return if (c1.isAfter(c2)) c1 else c2
        }
    }

    /**
     * The window boundary ("seam") trails [offsetMinutes] behind the current
     * moment and slides continuously forward with it, instead of flipping at
     * two fixed times. Clamp [offsetMinutes] to a sane range (e.g. 0..180) in
     * the UI - large values make the ring hard to read.
     */
    data class TrailingOffset(val offsetMinutes: Int) : DialTimeMapper {
        override fun windowStart(now: ZonedDateTime): ZonedDateTime = now.minusMinutes(offsetMinutes.toLong())
    }

    companion object {
        private fun mostRecentOccurrence(now: ZonedDateTime, timeOfDay: LocalTime): ZonedDateTime {
            var candidate = now.toLocalDate().atTime(timeOfDay).atZone(now.zone)
            if (candidate.isAfter(now)) candidate = candidate.minusDays(1)
            return candidate
        }

        /**
         * Within the unique 12h window starting at [windowStart], find the instant
         * whose hour-hand reading equals `angleDegrees / 30`. Works for any window
         * start because each hour-hand reading occurs exactly once per 12h window.
         */
        internal fun resolveWithWindow(windowStart: ZonedDateTime, angleDegrees: Double): ZonedDateTime {
            val targetMinutes = angleDegrees / 30.0 * 60.0 // 0..720
            val startMinuteOfDay = windowStart.hour * 60.0 + windowStart.minute + windowStart.second / 60.0
            val startMod = ((startMinuteOfDay % 720.0) + 720.0) % 720.0
            val offsetMinutes = (((targetMinutes - startMod) % 720.0) + 720.0) % 720.0
            return windowStart.plusSeconds((offsetMinutes * 60.0).toLong())
        }
    }
}
