package com.umfluten.lighttimeclouds.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.Locale

/**
 * Saved places, kept in the same DataStore as the widget settings (keys prefixed "place_<id>_")
 * so that migrating old per-widget coordinates into places happens in one atomic edit.
 */
class PlacesRepository(private val context: Context) {

    suspend fun all(): List<Place> {
        ensureInitialized()
        val prefs = context.settingsDataStore.data.first()
        return prefs.placeIds().mapNotNull { readPlace(prefs, it) }.sortedBy { it.name.lowercase(Locale.GERMAN) }
    }

    suspend fun save(place: Place) {
        context.settingsDataStore.edit { prefs -> writePlace(prefs, place) }
    }

    /** Ids of all places some widget (or the template) currently refers to. */
    suspend fun idsInUse(): Set<String> {
        val prefs = context.settingsDataStore.data.first()
        return prefs.asMap().filterKeys { it.name.endsWith(WidgetPrefsRepository.PLACE_KEY_SUFFIX) }
            .values.filterIsInstance<String>().toSet()
    }

    /** Deletes [id] unless a widget still uses it or it is the last place; returns whether it was deleted. */
    suspend fun delete(id: String): Boolean {
        if (id in idsInUse()) return false
        var deleted = false
        context.settingsDataStore.edit { prefs ->
            val ids = prefs.placeIds()
            if (id in ids && ids.size > 1) {
                prefs[PLACE_IDS] = ids - id
                val prefix = placePrefix(id)
                prefs.asMap().keys.filter { it.name.startsWith(prefix) }.forEach { prefs -= it }
                deleted = true
            }
        }
        return deleted
    }

    /**
     * Makes sure at least one place exists, and converts settings from before places existed: each
     * widget's own coordinates and label become a place (identical ones are merged) that the widget
     * then refers to. Old values only knew the device time zone, so migrated places get that zone.
     */
    suspend fun ensureInitialized() {
        val prefs = context.settingsDataStore.data.first()
        if (prefs.placeIds().isNotEmpty() && legacyWidgetIds(prefs).isEmpty()) return
        context.settingsDataStore.edit { mutable ->
            for (widgetId in legacyWidgetIds(mutable)) {
                val lat = mutable[doublePreferencesKey("w${widgetId}_lat")] ?: Place.DEFAULT.latitude
                val lon = mutable[doublePreferencesKey("w${widgetId}_lon")] ?: Place.DEFAULT.longitude
                val label = mutable[stringPreferencesKey("w${widgetId}_location_label")].orEmpty()
                val existing = mutable.placeIds().mapNotNull { readPlace(mutable, it) }
                    .firstOrNull { it.latitude == lat && it.longitude == lon && (label.isBlank() || it.name == label) }
                val place = existing ?: when {
                    label.isBlank() && lat == Place.DEFAULT.latitude && lon == Place.DEFAULT.longitude -> Place.DEFAULT
                    else -> Place.new(
                        name = label.ifBlank { "%.4f°, %.4f°".format(Locale.GERMAN, lat, lon) },
                        latitude = lat,
                        longitude = lon,
                    )
                }
                writePlace(mutable, place)
                mutable[WidgetPrefsRepository.placeKey(widgetId)] = place.id
                mutable -= doublePreferencesKey("w${widgetId}_lat")
                mutable -= doublePreferencesKey("w${widgetId}_lon")
                mutable -= stringPreferencesKey("w${widgetId}_location_label")
            }
            if (mutable.placeIds().isEmpty()) writePlace(mutable, Place.DEFAULT)
        }
    }

    companion object {
        private val PLACE_IDS = stringSetPreferencesKey("place_ids")
        private val LEGACY_LAT = Regex("""w(\d+)_lat""")

        private fun placePrefix(id: String) = "place_${id}_"
        private fun nameKey(id: String) = stringPreferencesKey(placePrefix(id) + "name")
        private fun latKey(id: String) = doublePreferencesKey(placePrefix(id) + "lat")
        private fun lonKey(id: String) = doublePreferencesKey(placePrefix(id) + "lon")
        private fun zoneKey(id: String) = stringPreferencesKey(placePrefix(id) + "zone")

        private fun Preferences.placeIds(): Set<String> = this[PLACE_IDS].orEmpty()

        private fun legacyWidgetIds(prefs: Preferences): List<Int> =
            prefs.asMap().keys.mapNotNull { LEGACY_LAT.matchEntire(it.name)?.groupValues?.get(1)?.toInt() }

        /** The place [id] from a preferences snapshot; null if unknown. An invalid zone falls back to the device zone. */
        internal fun readPlace(prefs: Preferences, id: String): Place? {
            if (id !in prefs.placeIds()) return null
            val zone = prefs[zoneKey(id)]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            return Place(
                id = id,
                name = prefs[nameKey(id)].orEmpty(),
                latitude = prefs[latKey(id)] ?: Place.DEFAULT.latitude,
                longitude = prefs[lonKey(id)] ?: Place.DEFAULT.longitude,
                zone = zone,
            )
        }

        /** First saved place by name, for widgets whose place is missing. */
        internal fun fallbackPlace(prefs: Preferences): Place =
            prefs.placeIds().mapNotNull { readPlace(prefs, it) }.minByOrNull { it.name.lowercase(Locale.GERMAN) }
                ?: Place.DEFAULT

        private fun writePlace(prefs: MutablePreferences, place: Place) {
            prefs[PLACE_IDS] = prefs.placeIds() + place.id
            prefs[nameKey(place.id)] = place.name
            prefs[latKey(place.id)] = place.latitude
            prefs[lonKey(place.id)] = place.longitude
            prefs[zoneKey(place.id)] = place.zone.id
        }
    }
}
