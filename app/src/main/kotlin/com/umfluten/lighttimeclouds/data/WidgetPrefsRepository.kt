package com.umfluten.lighttimeclouds.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Stores [WidgetSettings] per widget id (plus a [WidgetSettings.DEFAULT_WIDGET_ID] template) in a
 * single Preferences DataStore. Every key is prefixed with "w<id>_", so one widget's entries can be
 * removed as a whole. Missing or unreadable values fall back to the [WidgetSettings] defaults; the
 * place is stored as a reference into [PlacesRepository].
 */
class WidgetPrefsRepository(private val context: Context) {

    private class Keys(widgetId: Int) {
        private val prefix = prefixFor(widgetId)
        val place = placeKey(widgetId)
        val displayMode = stringPreferencesKey(prefix + "display_mode")
        val colorMode = stringPreferencesKey(prefix + "color_mode")
        val tapAction = stringPreferencesKey(prefix + "tap_action")
        val switchStrategy = stringPreferencesKey(prefix + "switch_strategy")
        val switchTime1 = intPreferencesKey(prefix + "switch_t1")
        val switchTime2 = intPreferencesKey(prefix + "switch_t2")
        val trailingOffset = intPreferencesKey(prefix + "trailing_offset")
        val ringStyle = stringPreferencesKey(prefix + "ring_style")
        val skyHorizon = stringPreferencesKey(prefix + "sky_horizon")
        val weatherRingPosition = stringPreferencesKey(prefix + "weather_ring_position")
        val minuteTicks = booleanPreferencesKey(prefix + "minute_ticks")
        val solarMarks = booleanPreferencesKey(prefix + "solar_marks")
        val sunriseMarks = booleanPreferencesKey(prefix + "sunrise_marks")
        val showMoon = booleanPreferencesKey(prefix + "show_moon")
        val showWeather = booleanPreferencesKey(prefix + "show_weather")
        val temperatureDisplay = stringPreferencesKey(prefix + "temperature_display")
        val temperaturePosition = stringPreferencesKey(prefix + "temperature_position")
        val labelPosition = stringPreferencesKey(prefix + "label_position")
        val configured = booleanPreferencesKey(prefix + "configured")
    }

    suspend fun load(widgetId: Int): WidgetSettings {
        PlacesRepository(context).ensureInitialized()
        val prefs = context.settingsDataStore.data.first()
        val k = Keys(widgetId)
        val d = WidgetSettings(widgetId = widgetId)
        return WidgetSettings(
            widgetId = widgetId,
            place = prefs[k.place]?.let { PlacesRepository.readPlace(prefs, it) } ?: PlacesRepository.fallbackPlace(prefs),
            displayMode = prefs.enumOr(k.displayMode, d.displayMode),
            colorMode = prefs.enumOr(k.colorMode, d.colorMode),
            tapAction = prefs.enumOr(k.tapAction, d.tapAction),
            switchStrategyType = prefs.enumOr(k.switchStrategy, d.switchStrategyType),
            switchTime1Minutes = prefs[k.switchTime1] ?: d.switchTime1Minutes,
            switchTime2Minutes = prefs[k.switchTime2] ?: d.switchTime2Minutes,
            trailingOffsetMinutes = prefs[k.trailingOffset] ?: d.trailingOffsetMinutes,
            ringStyle = prefs.enumOr(k.ringStyle, d.ringStyle),
            skyHorizon = prefs.enumOr(k.skyHorizon, d.skyHorizon),
            weatherRingPosition = prefs.enumOr(k.weatherRingPosition, d.weatherRingPosition),
            showMinuteTicks = prefs[k.minuteTicks] ?: d.showMinuteTicks,
            showSolarMarks = prefs[k.solarMarks] ?: d.showSolarMarks,
            showSunriseSunsetMarks = prefs[k.sunriseMarks] ?: d.showSunriseSunsetMarks,
            showMoon = prefs[k.showMoon] ?: d.showMoon,
            showWeather = prefs[k.showWeather] ?: d.showWeather,
            temperatureDisplay = prefs.enumOr(k.temperatureDisplay, d.temperatureDisplay),
            temperaturePosition = prefs.enumOr(k.temperaturePosition, d.temperaturePosition),
            labelPosition = prefs.enumOr(k.labelPosition, d.labelPosition),
        )
    }

    suspend fun save(settings: WidgetSettings) {
        val k = Keys(settings.widgetId)
        context.settingsDataStore.edit { prefs ->
            prefs[k.place] = settings.place.id
            prefs[k.displayMode] = settings.displayMode.name
            prefs[k.colorMode] = settings.colorMode.name
            prefs[k.tapAction] = settings.tapAction.name
            prefs[k.switchStrategy] = settings.switchStrategyType.name
            prefs[k.switchTime1] = settings.switchTime1Minutes
            prefs[k.switchTime2] = settings.switchTime2Minutes
            prefs[k.trailingOffset] = settings.trailingOffsetMinutes
            prefs[k.ringStyle] = settings.ringStyle.name
            prefs[k.skyHorizon] = settings.skyHorizon.name
            prefs[k.weatherRingPosition] = settings.weatherRingPosition.name
            prefs[k.minuteTicks] = settings.showMinuteTicks
            prefs[k.solarMarks] = settings.showSolarMarks
            prefs[k.sunriseMarks] = settings.showSunriseSunsetMarks
            prefs[k.showMoon] = settings.showMoon
            prefs[k.showWeather] = settings.showWeather
            prefs[k.temperatureDisplay] = settings.temperatureDisplay.name
            prefs[k.temperaturePosition] = settings.temperaturePosition.name
            prefs[k.labelPosition] = settings.labelPosition.name
            prefs[k.configured] = true
        }
    }

    suspend fun isConfigured(widgetId: Int): Boolean =
        context.settingsDataStore.data.first()[Keys(widgetId).configured] ?: false

    /** Removes every stored value of [widgetId], including keys of settings that no longer exist. */
    suspend fun delete(widgetId: Int) {
        val prefix = prefixFor(widgetId)
        context.settingsDataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { prefs -= it }
        }
    }

    companion object {
        internal const val PLACE_KEY_SUFFIX = "_place"

        private fun prefixFor(widgetId: Int) = "w${widgetId}_"

        internal fun placeKey(widgetId: Int) = stringPreferencesKey("w$widgetId$PLACE_KEY_SUFFIX")

        /** Stored enum name, or [fallback] if missing or no longer a valid constant (e.g. a removed option). */
        private inline fun <reified E : Enum<E>> Preferences.enumOr(key: Preferences.Key<String>, fallback: E): E =
            this[key]?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: fallback
    }
}
