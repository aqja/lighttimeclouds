package com.umfluten.lighttimeclouds.config

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.PlacesRepository
import com.umfluten.lighttimeclouds.data.roundCoordinate
import com.umfluten.lighttimeclouds.geo.SharedLocation
import com.umfluten.lighttimeclouds.geo.ZoneSuggestion
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Takes a location from a map app's "Share" or "Open with" into the saved places: only the
 * new-place dialog, floating over the other app, which comes back right after. No widget is
 * changed; the place is chosen for a widget later in its settings.
 */
class AddPlaceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val place = sharedPlace(intent)
        if (place == null) {
            // After a rotation the activity would be gone already, so the note shows only once.
            Toast.makeText(this, R.string.share_no_coordinates, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            ConfigTheme {
                PlaceEditorDialog(
                    place = place,
                    isNew = true,
                    onSave = ::save,
                    onDismiss = ::finish,
                )
            }
        }
    }

    private fun save(place: Place) {
        lifecycleScope.launch {
            PlacesRepository(applicationContext).save(place)
            Toast.makeText(applicationContext, getString(R.string.share_place_added, place.name), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /** A new, unsaved place from a shared `geo:` URI or text, with the time zone suggested from its coordinates. */
    private fun sharedPlace(intent: Intent?): Place? {
        val shared = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        val location = shared?.let(SharedLocation::parse) ?: return null
        val latitude = roundCoordinate(location.latitude)
        val longitude = roundCoordinate(location.longitude)
        return Place.new(
            name = location.name.orEmpty(),
            latitude = latitude,
            longitude = longitude,
            zone = ZoneSuggestion.nearest(latitude, longitude) ?: ZoneId.systemDefault(),
        )
    }
}
