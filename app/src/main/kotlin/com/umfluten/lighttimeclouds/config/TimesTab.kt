package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.astro.MoonEventType
import com.umfluten.lighttimeclouds.astro.SolarDay
import com.umfluten.lighttimeclouds.astro.SolarEventType
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.widget.ClockFaceRenderer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm zzz", Locale.GERMAN)
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)

/**
 * All sun events of the current solar day at the widget's (possibly not yet saved) place, in that
 * place's time zone, followed by the moon's phase, rise and set.
 * Past events are dimmed, the next one is highlighted; refreshes every minute.
 */
@Composable
internal fun TimesTab(settings: WidgetSettings, padding: PaddingValues) {
    val place = settings.place
    var now by remember(place.zone) { mutableStateOf(ZonedDateTime.now(place.zone)) }
    LaunchedEffect(place.zone) {
        while (true) {
            delay((60 - ZonedDateTime.now().second) * 1000L)
            now = ZonedDateTime.now(place.zone)
        }
    }
    val events = remember(place, now) {
        SolarDay.eventsAround(now.toInstant(), now.zone, place.latitude, place.longitude)
    }
    val moonEvents = remember(events) {
        val start = events.first().instant
        val end = events.last().instant
        if (start == null || end == null) {
            emptyList()
        } else {
            SolarDay.moonEventsBetween(start, end, now.zone, place.latitude, place.longitude)
        }
    }
    val firstDate = events.first().instant?.atZone(now.zone)?.toLocalDate()
    val nextIndex = events.indexOfFirst { it.instant?.isAfter(now.toInstant()) == true }
    val nextMoonIndex = moonEvents.indexOfFirst { it.instant.isAfter(now.toInstant()) }

    LazyColumn(contentPadding = padding) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "%.4f°, %.4f° · %s".format(Locale.GERMAN, place.latitude, place.longitude, place.zone.id),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Sonnentag von Sonnenmitternacht bis Sonnenmitternacht. Die Liste wechselt zu Sonnenmitternacht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(events.size) { index ->
            val event = events[index]
            // Dot in the ring color of the phase that begins at this event (just after it).
            val dotColor = event.instant?.let { instant ->
                val altitude = SunMoonCalculator.sunAltitudeDegrees(instant.plusSeconds(60), place.latitude, place.longitude)
                Color(ClockFaceRenderer.ringColor(altitude, settings))
            }
            EventRow(
                label = solarEventLabel(event.type),
                instant = event.instant,
                now = now,
                isNext = index == nextIndex,
                showDate = event.instant?.atZone(now.zone)?.toLocalDate() != firstDate,
                dotColor = dotColor,
            )
        }

        item {
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                "Mond",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        item {
            val illumination = SunMoonCalculator.moonIllumination(now.toInstant())
            ListItem(
                headlineContent = { Text("Mondphase") },
                supportingContent = { Text(moonPhaseLabel(SunMoonCalculator.moonPhaseOf(illumination.phase))) },
                trailingContent = { Text("%d %% beleuchtet".format((illumination.fraction * 100).toInt())) },
            )
        }
        if (moonEvents.isEmpty()) {
            item {
                ListItem(
                    headlineContent = { Text("Kein Mondauf- oder -untergang an diesem Sonnentag") },
                    supportingContent = {
                        val up = SunMoonCalculator.moonAltitudeDegrees(now.toInstant(), place.latitude, place.longitude) > 0
                        Text(if (up) "Der Mond bleibt über dem Horizont." else "Der Mond bleibt unter dem Horizont.")
                    },
                )
            }
        }
        items(moonEvents.size) { index ->
            val event = moonEvents[index]
            EventRow(
                label = if (event.type == MoonEventType.MOONRISE) "Mondaufgang" else "Monduntergang",
                instant = event.instant,
                now = now,
                isNext = index == nextMoonIndex,
                showDate = event.instant.atZone(now.zone).toLocalDate() != firstDate,
                dotColor = Color(ClockFaceRenderer.MARK_COLOR),
            )
        }
    }
}

/** One event line: color dot, label, optional date, and time; dimmed if past, highlighted if next. */
@Composable
private fun EventRow(
    label: String,
    instant: Instant?,
    now: ZonedDateTime,
    isNext: Boolean,
    showDate: Boolean,
    dotColor: Color?,
) {
    val local = instant?.atZone(now.zone)
    val isPast = instant?.isAfter(now.toInstant()) == false
    val contentColor = when {
        isNext -> MaterialTheme.colorScheme.primary
        isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val weight = if (isNext) FontWeight.Bold else null
    ListItem(
        leadingContent = {
            Box(
                Modifier
                    .size(14.dp)
                    .background(dotColor ?: Color.Transparent, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
        },
        headlineContent = { Text(label, color = contentColor, fontWeight = weight) },
        supportingContent = when {
            local == null -> ({ Text("tritt an diesem Tag nicht ein", color = contentColor) })
            showDate -> ({ Text(local.format(DATE_FORMAT), color = contentColor) })
            else -> null
        },
        trailingContent = { Text(local?.format(TIME_FORMAT) ?: "–", color = contentColor, fontWeight = weight) },
    )
}

private fun moonPhaseLabel(phase: SunMoonCalculator.MoonPhase): String = when (phase) {
    SunMoonCalculator.MoonPhase.NEW_MOON -> "Neumond"
    SunMoonCalculator.MoonPhase.WAXING_CRESCENT -> "Zunehmende Sichel"
    SunMoonCalculator.MoonPhase.FIRST_QUARTER -> "Erstes Viertel (zunehmender Halbmond)"
    SunMoonCalculator.MoonPhase.WAXING_GIBBOUS -> "Zunehmender Mond"
    SunMoonCalculator.MoonPhase.FULL_MOON -> "Vollmond"
    SunMoonCalculator.MoonPhase.WANING_GIBBOUS -> "Abnehmender Mond"
    SunMoonCalculator.MoonPhase.LAST_QUARTER -> "Letztes Viertel (abnehmender Halbmond)"
    SunMoonCalculator.MoonPhase.WANING_CRESCENT -> "Abnehmende Sichel"
}

private fun solarEventLabel(type: SolarEventType): String = when (type) {
    SolarEventType.SOLAR_MIDNIGHT -> "Sonnenmitternacht"
    SolarEventType.ASTRONOMICAL_DAWN -> "Beginn astronomische Dämmerung"
    SolarEventType.NAUTICAL_DAWN -> "Beginn nautische Dämmerung"
    SolarEventType.CIVIL_DAWN -> "Beginn bürgerliche Dämmerung"
    SolarEventType.SUNRISE -> "Sonnenaufgang"
    SolarEventType.SUNRISE_END -> "Ende Sonnenaufgang"
    SolarEventType.GOLDEN_HOUR_END -> "Ende goldene Stunde"
    SolarEventType.SOLAR_NOON -> "Sonnenmittag"
    SolarEventType.GOLDEN_HOUR_START -> "Beginn goldene Stunde"
    SolarEventType.SUNSET_START -> "Beginn Sonnenuntergang"
    SolarEventType.SUNSET -> "Sonnenuntergang"
    SolarEventType.CIVIL_DUSK -> "Ende bürgerliche Dämmerung"
    SolarEventType.NAUTICAL_DUSK -> "Ende nautische Dämmerung"
    SolarEventType.ASTRONOMICAL_DUSK -> "Ende astronomische Dämmerung"
}
