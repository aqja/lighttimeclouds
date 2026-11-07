package com.umfluten.lighttimeclouds.config

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.PlacesRepository
import com.umfluten.lighttimeclouds.data.SettingsFile
import com.umfluten.lighttimeclouds.data.WidgetPrefsRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.widget.ClockWidgetProvider
import com.umfluten.lighttimeclouds.widget.WeatherSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings of one widget (or the template) being edited, plus the shared list of saved places.
 * Widget settings are only persisted on [save]; place edits apply immediately to all widgets,
 * since places are shared.
 */
class ConfigViewModel(application: Application, private val widgetId: Int) : AndroidViewModel(application) {
    private val repository = WidgetPrefsRepository(application)
    private val placesRepository = PlacesRepository(application)

    /** Null until the persisted (or default) values have been loaded from disk. */
    private val _settings = MutableStateFlow<WidgetSettings?>(null)
    val settings: StateFlow<WidgetSettings?> = _settings

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places

    /** Places referenced by any saved widget or the template; these cannot be deleted. */
    private val _placesInUse = MutableStateFlow<Set<String>>(emptySet())
    val placesInUse: StateFlow<Set<String>> = _placesInUse

    init {
        viewModelScope.launch {
            // What the widget currently shows, so a never-saved widget opens with the template.
            _settings.value = ClockWidgetProvider.effectiveSettings(application, widgetId)
            refreshPlaces()
        }
    }

    fun update(transform: (WidgetSettings) -> WidgetSettings) {
        _settings.value = _settings.value?.let(transform)
    }

    fun save(onSaved: () -> Unit) {
        val current = _settings.value ?: return
        viewModelScope.launch {
            repository.save(current)
            if (widgetId != WidgetSettings.DEFAULT_WIDGET_ID) {
                // The most recently saved settings become the template for newly placed widgets.
                repository.save(current.copy(widgetId = WidgetSettings.DEFAULT_WIDGET_ID))
            }
            WeatherSync.update(getApplication(), refreshNow = current.showWeather)
            onSaved()
        }
    }

    /** Stores [place] (new or edited), selects it for this widget, and redraws widgets using it. */
    fun savePlace(place: Place) {
        viewModelScope.launch {
            placesRepository.save(place)
            update { it.copy(place = place) }
            refreshPlaces()
            ClockWidgetProvider.updateAll(getApplication())
            // New coordinates invalidate the place's cached forecast.
            WeatherSync.update(getApplication(), refreshNow = true)
        }
    }

    fun deletePlace(place: Place) {
        viewModelScope.launch {
            placesRepository.delete(place.id)
            refreshPlaces()
        }
    }

    /** Writes this widget's settings as edited so far and all places to [uri]; tells [onDone] whether that worked. */
    fun exportTo(uri: Uri, onDone: (Boolean) -> Unit) {
        val current = _settings.value ?: return
        viewModelScope.launch {
            val written = withContext(Dispatchers.IO) {
                runCatching {
                    val text = SettingsFile.encode(current, placesRepository.all())
                    // "wt" cuts off what a longer file had there before.
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            onDone(written)
        }
    }

    /**
     * Reads a settings file from [uri]: its places are stored right away, like edited ones, and its
     * settings fill this editor, to be kept with [save]. Tells [onDone] the number of places, or
     * null if the file could not be read.
     */
    fun importFrom(uri: Uri, onDone: (Int?) -> Unit) {
        viewModelScope.launch {
            val contents = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    SettingsFile.decode(text, widgetId)
                }.getOrNull()
            }
            if (contents == null) {
                onDone(null)
                return@launch
            }
            contents.places.forEach { placesRepository.save(it) }
            _settings.value = contents.settings
            refreshPlaces()
            ClockWidgetProvider.updateAll(getApplication())
            // Imported coordinates invalidate the cached forecast of a place, as edited ones do.
            WeatherSync.update(getApplication(), refreshNow = true)
            onDone(contents.places.size)
        }
    }

    private suspend fun refreshPlaces() {
        _places.value = placesRepository.all()
        _placesInUse.value = placesRepository.idsInUse()
    }

    class Factory(private val application: Application, private val widgetId: Int) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ConfigViewModel(application, widgetId) as T
    }
}
