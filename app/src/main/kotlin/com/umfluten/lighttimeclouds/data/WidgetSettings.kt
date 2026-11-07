package com.umfluten.lighttimeclouds.data

import com.umfluten.lighttimeclouds.sky.SkyProjection

enum class DisplayMode { TWELVE_HOUR, TWENTY_FOUR_HOUR }

enum class TapAction { OWN_APP, SYSTEM_CLOCK }

/** Light/dark variant of the dial chrome (numerals, ticks, hands); [AUTO] follows the system dark mode. */
enum class ColorMode { LIGHT, DARK, AUTO }

/** Where the shown window begins: at fixed times, or right at the current time, so that the hand always sits at its start. */
enum class SwitchStrategyType { FIXED_TIMES, TRAILING }

/** Where the place name is shown in the widget, if at all. */
enum class LabelPosition { NONE, ABOVE, BELOW }

/**
 * How the day/night ring is colored: [SKY] shows the modeled sky from the horizon upward across
 * the ring's width (see [SkyHorizon]); [BANDS] one fixed color per sky phase.
 */
enum class RingStyle { SKY, BANDS }

/** Whether the horizon is at the inner or the outer edge of the ring (sky gradient, sun and moon in the ring). */
enum class SkyHorizon { INNER, OUTER }

/**
 * How the moon is shown: [ON_HOUR_HAND] as a framed disc on the hour hand, its daily path plotted
 * against clock time (so the path crosses the horizon circle at the actual moonrise/moonset times);
 * [SEPARATE_HAND] as its own hand at "now minus the moon's lag behind the sun", showing where the
 * moon is in its daily circuit compared with the sun.
 */
enum class MoonStyle { ON_HOUR_HAND, SEPARATE_HAND }

/** Where the weather ring sits: just inside the day/night ring, or outermost around it. */
enum class WeatherRingPosition { INSIDE, OUTSIDE }

/** Where the temperature labels sit: between the hour numerals, or as a scale around the outermost ring. */
enum class TemperaturePosition { BETWEEN_NUMERALS, OUTSIDE }

/** Where a short weather assessment (see WeatherOutlook) is shown next to the clock, if at all. */
enum class SummaryPosition { NONE, LEFT, RIGHT, BELOW }

/** Hourly temperature labels around the dial (only with the weather ring): none, air or apparent ("feels like") temperature. */
enum class TemperatureDisplay { NONE, AIR, APPARENT }

/**
 * All per-widget-instance configuration. [widgetId] is the real AppWidget id,
 * or [DEFAULT_WIDGET_ID] for the template used as the default for newly
 * placed widgets and edited from the launcher icon when no instance exists yet.
 */
data class WidgetSettings(
    val widgetId: Int,
    val place: Place = Place.DEFAULT,
    val displayMode: DisplayMode = DisplayMode.TWELVE_HOUR,
    val colorMode: ColorMode = ColorMode.AUTO,
    val tapAction: TapAction = TapAction.SYSTEM_CLOCK,
    val switchStrategyType: SwitchStrategyType = SwitchStrategyType.FIXED_TIMES,
    val switchTime1Minutes: Int = 6 * 60,
    val switchTime2Minutes: Int = 18 * 60,
    /** The one fixed switch time of the 24h dial, where its shown day begins. */
    val switchTime24hMinutes: Int = 0,
    /**
     * Dial degrees (0..[MAX_RING_END_FADE_DEG]) before the end of the shown window over which the
     * day/night ring fades out, so its last stretch is not mistaken for the time right after its
     * start; never before the current time.
     */
    val ringEndFadeDeg: Int = 15,
    val ringStyle: RingStyle = RingStyle.SKY,
    val skyHorizon: SkyHorizon = SkyHorizon.INNER,
    /**
     * Elevation above the horizon (degrees) that the ring's far edge stands for: the sky gradient
     * spans up to it, and sun and moon in the ring are placed on this scale. At most
     * [com.umfluten.lighttimeclouds.sky.SkyProjection.MAX_TOP_ELEVATION_DEG].
     */
    val skyTopElevationDeg: Int = 45,
    /** The sun in the day/night ring at the current time, as far from the horizon edge as it is high. */
    val showSunInRing: Boolean = true,
    /** Like [showSunInRing], for the moon with its phase. */
    val showMoonInRing: Boolean = true,
    /** With [showMoonInRing]: the moon's altitude over the whole shown time span as a thin line in the ring. */
    val showMoonPathInRing: Boolean = true,
    val showMinuteTicks: Boolean = false,
    val showSolarMarks: Boolean = true,
    val showSunriseSunsetMarks: Boolean = true,
    val showMoon: Boolean = true,
    val moonStyle: MoonStyle = MoonStyle.ON_HOUR_HAND,
    /** Letters on the moon path where the moon passes north, east, south and west. */
    val showMoonCompass: Boolean = true,
    /** Red tick at the next alarm (the only one Android exposes), if it falls into the shown time span. */
    val showNextAlarm: Boolean = false,
    /** Weather ring from an online forecast; off by default so nothing is fetched without the user opting in. */
    val showWeather: Boolean = false,
    /** Cloud cover and temperatures default to the outside of the day/night ring, leaving the dial uncluttered. */
    val weatherRingPosition: WeatherRingPosition = WeatherRingPosition.OUTSIDE,
    val temperatureDisplay: TemperatureDisplay = TemperatureDisplay.AIR,
    val temperaturePosition: TemperaturePosition = TemperaturePosition.OUTSIDE,
    /** Only with the weather ring, whose forecast it is based on. */
    val summaryPosition: SummaryPosition = SummaryPosition.NONE,
    val labelPosition: LabelPosition = LabelPosition.NONE,
) {
    companion object {
        // Deliberately the same value as AppWidgetManager.INVALID_APPWIDGET_ID: real widget
        // ids are always > 0, so 0 doubles as both "no specific widget" and "template".
        const val DEFAULT_WIDGET_ID = 0

        const val MAX_RING_END_FADE_DEG = 30
        const val MIN_SKY_TOP_ELEVATION_DEG = 10
        const val MAX_SKY_TOP_ELEVATION_DEG = SkyProjection.MAX_TOP_ELEVATION_DEG.toInt()
    }
}
