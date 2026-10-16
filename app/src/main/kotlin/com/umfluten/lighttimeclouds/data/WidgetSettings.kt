package com.umfluten.lighttimeclouds.data

enum class DisplayMode { TWELVE_HOUR, TWENTY_FOUR_HOUR }

enum class TapAction { OWN_APP, SYSTEM_CLOCK }

/** Light/dark variant of the dial chrome (numerals, ticks, hands); [AUTO] follows the system dark mode. */
enum class ColorMode { LIGHT, DARK, AUTO }

enum class SwitchStrategyType { FIXED_TIMES, TRAILING_OFFSET }

/** Where the place name is shown in the widget, if at all. */
enum class LabelPosition { NONE, ABOVE, BELOW }

/** How the day/night ring is colored: a smooth gradient over the sun altitude, or one fixed color per sky phase. */
enum class RingStyle { GRADIENT, BANDS }

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
    val trailingOffsetMinutes: Int = 30,
    val ringStyle: RingStyle = RingStyle.GRADIENT,
    val showMinuteTicks: Boolean = false,
    val showSolarMarks: Boolean = true,
    val showSunriseSunsetMarks: Boolean = true,
    val showMoon: Boolean = true,
    val labelPosition: LabelPosition = LabelPosition.NONE,
) {
    companion object {
        // Deliberately the same value as AppWidgetManager.INVALID_APPWIDGET_ID: real widget
        // ids are always > 0, so 0 doubles as both "no specific widget" and "template".
        const val DEFAULT_WIDGET_ID = 0
    }
}
