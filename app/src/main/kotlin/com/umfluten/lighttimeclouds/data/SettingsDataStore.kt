package com.umfluten.lighttimeclouds.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single Preferences DataStore for widget settings and saved places. */
internal val Context.settingsDataStore by preferencesDataStore(name = "widget_prefs")
