package com.umfluten.lighttimeclouds.config

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.astro.MoonEventType
import com.umfluten.lighttimeclouds.astro.SolarDay
import com.umfluten.lighttimeclouds.astro.SolarEventType
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.widget.skyColors
import java.time.Instant
import java.time.ZonedDateTime

/**
 * The moon's phase, rise and set side by side, below them all sun events of the current solar day,
 * at the widget's (possibly not yet saved) place, in that place's time zone. Dates only show where
 * they change. Past events are dimmed, the next one is highlighted; refreshes every minute.
 */
@Composable
internal fun TimesTab(settings: WidgetSettings, padding: PaddingValues) {
    val place = settings.place
    val now = rememberMinuteClock(place.zone)
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
    val nextIndex = events.indexOfFirst { it.instant?.isAfter(now.toInstant()) == true }
    val nextMoonIndex = moonEvents.indexOfFirst { it.instant.isAfter(now.toInstant()) }
    val formats = rememberUiFormats()

    LazyColumn(contentPadding = padding) {
        item {
            val heading = MaterialTheme.typography.titleMedium.toSpanStyle()
            Text(
                buildAnnotatedString {
                    withStyle(heading) { append(place.name) }
                    append(" · " + formats.coordinates(place.latitude, place.longitude) + " · " + place.zone.id)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        item {
            val illumination = SunMoonCalculator.moonIllumination(now.toInstant())
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledValue(
                    label = stringResource(R.string.moon_phase),
                    value = stringResource(moonPhaseLabel(SunMoonCalculator.moonPhaseOf(illumination.phase))),
                    note = stringResource(R.string.moon_illuminated, (illumination.fraction * 100).toInt()),
                    weight = MOON_PHASE_WEIGHT,
                )
                if (moonEvents.isEmpty()) {
                    val up = SunMoonCalculator.moonAltitudeDegrees(now.toInstant(), place.latitude, place.longitude) > 0
                    LabeledValue(
                        label = stringResource(R.string.moon_no_events),
                        value = stringResource(if (up) R.string.moon_stays_up else R.string.moon_stays_down),
                    )
                }
                moonEvents.forEachIndexed { index, event ->
                    val local = event.instant.atZone(now.zone)
                    val isNext = index == nextMoonIndex
                    LabeledValue(
                        label = stringResource(if (event.type == MoonEventType.MOONRISE) R.string.moonrise else R.string.moonset),
                        // Without the zone, which the line above names, so three columns fit in any language.
                        value = formats.timeOfDay(local.hour * 60 + local.minute),
                        note = if (local.toLocalDate() != now.toLocalDate()) formats.date(local) else "",
                        color = timelineColor(highlighted = isNext, past = event.instant.isBefore(now.toInstant())),
                        fontWeight = if (isNext) FontWeight.Bold else null,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
        items(events.size) { index ->
            val event = events[index]
            // A slice of the sky at the phase that begins at this event (just after it), with the
            // horizon at the bottom, where twilight shows its colors.
            val skySlice = event.instant?.let { instant ->
                val altitude = SunMoonCalculator.sunAltitudeDegrees(instant.plusSeconds(60), place.latitude, place.longitude)
                Brush.verticalGradient(skyColors(altitude, SKY_SLICE_TOP_DEG).map(::Color).reversed())
            }
            val previous = events.take(index).lastOrNull { it.instant != null }?.instant
            EventRow(
                label = stringResource(solarEventLabel(event.type)),
                instant = event.instant,
                now = now,
                formats = formats,
                isNext = index == nextIndex,
                showDate = previous != null && event.instant?.atZone(now.zone)?.toLocalDate() != previous.atZone(now.zone).toLocalDate(),
                marker = skySlice,
            )
        }
    }
}

/** Sun events: a square slice of the ring, so the horizon at its bottom edge stands out. */
private val SKY_SLICE_SHAPE = RoundedCornerShape(3.dp)

/**
 * How high the slices reach above the horizon, the same for every widget, so the list always shows
 * twilight's colors at the horizon about equally large.
 */
private const val SKY_SLICE_TOP_DEG = 30.0
private val SKY_SLICE_SIZE = 24.dp
private const val MOON_PHASE_WEIGHT = 1.4f

/** One event line: [marker] (nothing while null), label, optional date, and time; dimmed if past, highlighted if next. */
@Composable
private fun EventRow(
    label: String,
    instant: Instant?,
    now: ZonedDateTime,
    formats: UiFormats,
    isNext: Boolean,
    showDate: Boolean,
    marker: Brush?,
) {
    val local = instant?.atZone(now.zone)
    val isPast = instant?.isAfter(now.toInstant()) == false
    val contentColor = timelineColor(highlighted = isNext, past = isPast)
    val weight = if (isNext) FontWeight.Bold else null
    ListItem(
        leadingContent = {
            // The outline lies around the fill, not over its edge, which keeps the horizon in view.
            Box(
                Modifier
                    .size(SKY_SLICE_SIZE)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SKY_SLICE_SHAPE)
                    .padding(1.dp)
                    .then(if (marker != null) Modifier.background(marker, SKY_SLICE_SHAPE) else Modifier),
            )
        },
        headlineContent = { Text(label, color = contentColor, fontWeight = weight) },
        supportingContent = when {
            local == null -> ({ Text(stringResource(R.string.event_does_not_occur), color = contentColor) })
            showDate -> ({ Text(formats.date(local), color = contentColor) })
            else -> null
        },
        trailingContent = { Text(local?.let(formats::time) ?: "–", color = contentColor, fontWeight = weight) },
    )
}

@StringRes
private fun moonPhaseLabel(phase: SunMoonCalculator.MoonPhase): Int = when (phase) {
    SunMoonCalculator.MoonPhase.NEW_MOON -> R.string.moon_phase_new
    SunMoonCalculator.MoonPhase.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
    SunMoonCalculator.MoonPhase.FIRST_QUARTER -> R.string.moon_phase_first_quarter
    SunMoonCalculator.MoonPhase.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
    SunMoonCalculator.MoonPhase.FULL_MOON -> R.string.moon_phase_full
    SunMoonCalculator.MoonPhase.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
    SunMoonCalculator.MoonPhase.LAST_QUARTER -> R.string.moon_phase_last_quarter
    SunMoonCalculator.MoonPhase.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
}

@StringRes
private fun solarEventLabel(type: SolarEventType): Int = when (type) {
    SolarEventType.SOLAR_MIDNIGHT -> R.string.solar_midnight
    SolarEventType.ASTRONOMICAL_DAWN -> R.string.astronomical_dawn
    SolarEventType.NAUTICAL_DAWN -> R.string.nautical_dawn
    SolarEventType.CIVIL_DAWN -> R.string.civil_dawn
    SolarEventType.SUNRISE -> R.string.sunrise
    SolarEventType.SUNRISE_END -> R.string.sunrise_end
    SolarEventType.GOLDEN_HOUR_END -> R.string.golden_hour_end
    SolarEventType.SOLAR_NOON -> R.string.solar_noon
    SolarEventType.GOLDEN_HOUR_START -> R.string.golden_hour_start
    SolarEventType.SUNSET_START -> R.string.sunset_start
    SolarEventType.SUNSET -> R.string.sunset
    SolarEventType.CIVIL_DUSK -> R.string.civil_dusk
    SolarEventType.NAUTICAL_DUSK -> R.string.nautical_dusk
    SolarEventType.ASTRONOMICAL_DUSK -> R.string.astronomical_dusk
}
