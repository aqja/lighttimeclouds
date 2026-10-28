package com.umfluten.lighttimeclouds.data

enum class DisplayMode { TWELVE_HOUR, TWENTY_FOUR_HOUR }

enum class TapAction { OWN_APP, SYSTEM_CLOCK }

/** Light/dark variant of the dial chrome (numerals, ticks, hands); [AUTO] follows the system dark mode. */
enum class ColorMode { LIGHT, DARK, AUTO }

enum class SwitchStrategyType { FIXED_TIMES, TRAILING_OFFSET }

/** Where the place name is shown in the widget, if at all. */
enum class LabelPosition { NONE, ABOVE, BELOW }

/**
 * How the day/night ring is colored: [SKY] shows the modeled sky from the horizon upward across
 * the ring's width (see [SkyHorizon]); [GRADIENT] one color per sun altitude; [BANDS] one fixed
 * color per sky phase.
 */
enum class RingStyle { SKY, GRADIENT, BANDS }

/** For [RingStyle.SKY]: whether the horizon is at the inner or the outer edge of the ring. */
enum class SkyHorizon { INNER, OUTER }

/** Where the weather ring sits: just inside the day/night ring, or outermost around it. */
enum class WeatherRingPosition { INSIDE, OUTSIDE }

/** Where the temperature labels sit: between the hour numerals, or as a scale around the outermost ring. */
enum class TemperaturePosition { BETWEEN_NUMERALS, OUTSIDE }

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
    val trailingOffsetMinutes: Int = 30,
    val ringStyle: RingStyle = RingStyle.SKY,
    val skyHorizon: SkyHorizon = SkyHorizon.INNER,
    val showMinuteTicks: Boolean = false,
    val showSolarMarks: Boolean = true,
    val showSunriseSunsetMarks: Boolean = true,
    val showMoon: Boolean = true,
    /** Red tick at the next alarm (the only one Android exposes), if it falls into the shown time span. */
    val showNextAlarm: Boolean = false,
    /** Weather ring from an online forecast; off by default so nothing is fetched without the user opting in. */
    val showWeather: Boolean = false,
    val weatherRingPosition: WeatherRingPosition = WeatherRingPosition.INSIDE,
    val temperatureDisplay: TemperatureDisplay = TemperatureDisplay.NONE,
    val temperaturePosition: TemperaturePosition = TemperaturePosition.BETWEEN_NUMERALS,
    val labelPosition: LabelPosition = LabelPosition.NONE,
) {
    companion object {
        // Deliberately the same value as AppWidgetManager.INVALID_APPWIDGET_ID: real widget
        // ids are always > 0, so 0 doubles as both "no specific widget" and "template".
        const val DEFAULT_WIDGET_ID = 0
    }
}
