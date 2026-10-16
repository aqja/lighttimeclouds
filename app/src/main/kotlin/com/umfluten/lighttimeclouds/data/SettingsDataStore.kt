package com.umfluten.lighttimeclouds.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single Preferences DataStore for widget settings and saved places (file name kept from v1 for compatibility). */
internal val Context.settingsDataStore by preferencesDataStore(name = "widget_prefs")
