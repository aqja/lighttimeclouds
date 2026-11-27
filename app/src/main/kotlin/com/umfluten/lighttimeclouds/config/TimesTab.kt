package com.umfluten.lighttimeclouds.config

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.astro.Apsis
import com.umfluten.lighttimeclouds.astro.EclipseKind
import com.umfluten.lighttimeclouds.astro.LocalLunarEvent
import com.umfluten.lighttimeclouds.astro.LunarEclipse
import com.umfluten.lighttimeclouds.astro.LunarEvent
import com.umfluten.lighttimeclouds.astro.LunarEvents
import com.umfluten.lighttimeclouds.astro.LunarStandstill
import com.umfluten.lighttimeclouds.astro.MoonEventType
import com.umfluten.lighttimeclouds.astro.NotableFullMoon
import com.umfluten.lighttimeclouds.astro.SolarDay
import com.umfluten.lighttimeclouds.astro.SolarEventType
import com.umfluten.lighttimeclouds.astro.SolarEclipse
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.astro.Visibility
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.widget.skyColors
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The moon's phase, which opens to the dates of the coming cycle, its rise and set, its coming
 * special events (perigee and apogee, supermoon, eclipses, standstills), then all sun
 * events of the current solar day, at the widget's (possibly not yet saved) place, in that place's
 * time zone. Dates only show where they change. Past events are dimmed, the next one is
 * highlighted; refreshes every minute.
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
    // Searching years ahead for eclipses takes a moment: off the main thread, once an hour.
    val hour = now.toInstant().truncatedTo(ChronoUnit.HOURS)
    val lunarEvents by produceState<List<LocalLunarEvent>?>(null, place.latitude, place.longitude, hour) {
        value = withContext(Dispatchers.Default) { LunarEvents.upcoming(now.toInstant(), place.latitude, place.longitude) }
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
            // The phase, which opens to the coming cycle, then moonrise and moonset.
            val rowModifier = Modifier.padding(horizontal = 16.dp)
            val spacing = Arrangement.spacedBy(8.dp)
            var phasesOpen by rememberSaveable { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .expandable(phasesOpen) { phasesOpen = !phasesOpen }
                    .then(rowModifier),
                horizontalArrangement = spacing,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LabeledValue(
                    label = stringResource(R.string.moon_phase),
                    value = stringResource(moonPhaseLabel(SunMoonCalculator.moonPhaseOf(illumination.phase))),
                    note = stringResource(R.string.moon_illuminated, (illumination.fraction * 100).toInt()),
                )
                ExpandArrow(phasesOpen)
            }
            AnimatedVisibility(phasesOpen) {
                MoonPhaseList(now, formats, rowModifier)
            }
            Row(rowModifier.padding(top = 8.dp), horizontalArrangement = spacing) {
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
                        // Without the zone, which the heading names.
                        value = formats.timeOfDay(local.hour * 60 + local.minute),
                        note = formats.date(local),
                        color = timelineColor(highlighted = isNext, past = event.instant.isBefore(now.toInstant())),
                        fontWeight = if (isNext) FontWeight.Bold else null,
                    )
                }
                // A lone moonrise or moonset keeps the width of one column.
                if (moonEvents.size == 1) Spacer(Modifier.weight(1f))
            }
            var lunarEventsOpen by rememberSaveable { mutableStateOf(false) }
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .expandable(lunarEventsOpen) { lunarEventsOpen = !lunarEventsOpen }
                    .then(rowModifier),
                horizontalArrangement = spacing,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val next = lunarEvents?.firstOrNull()?.event
                LabeledValue(
                    label = stringResource(R.string.moon_events),
                    value = next?.let { lunarEventLabel(it) } ?: "…",
                    note = next?.let { lunarEventDate(it, now, formats) }.orEmpty(),
                )
                ExpandArrow(lunarEventsOpen)
            }
            AnimatedVisibility(lunarEventsOpen) {
                LunarEventList(lunarEvents.orEmpty(), now, place.latitude, formats, rowModifier)
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

/**
 * The eight phases at their next moment or beginning, in order of time, the date leading, since
 * they lie days or weeks ahead.
 */
@Composable
private fun MoonPhaseList(now: ZonedDateTime, formats: UiFormats, modifier: Modifier) {
    val upcoming = remember(now) { SunMoonCalculator.upcomingMoonPhases(now.toInstant()) }
    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        upcoming.forEach { (phase, instant) ->
            val local = instant.atZone(now.zone)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(moonPhaseLabel(phase)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(formats.date(local), style = MaterialTheme.typography.bodyMedium)
                Text(
                    formats.timeOfDay(local.hour * 60 + local.minute),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Hint(stringResource(R.string.moon_phases_hint))
    }
}

/**
 * The coming special events of the moon, each kind once, the date leading, with what makes each
 * one stand out below it.
 */
@Composable
private fun LunarEventList(events: List<LocalLunarEvent>, now: ZonedDateTime, latitude: Double, formats: UiFormats, modifier: Modifier) {
    Column(modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        events.forEach { item ->
            val event = item.event
            val local = event.instant.atZone(now.zone)
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(lunarEventLabel(event), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(lunarEventDate(event, now, formats), style = MaterialTheme.typography.bodyMedium)
                    if (event !is LunarStandstill) {
                        Text(
                            formats.timeOfDay(local.hour * 60 + local.minute),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    lunarEventNote(item, now, latitude, formats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Hint(stringResource(R.string.moon_events_hint))
    }
}

@Composable
private fun lunarEventLabel(event: LunarEvent): String = stringResource(
    when (event) {
        is Apsis -> if (event.perigee) R.string.perigee else R.string.apogee
        is NotableFullMoon -> if (event.supermoon) R.string.supermoon else R.string.micromoon
        is LunarEclipse -> when (event.kind) {
            EclipseKind.TOTAL -> R.string.lunar_eclipse_total
            EclipseKind.PENUMBRAL -> R.string.lunar_eclipse_penumbral
            else -> R.string.lunar_eclipse_partial
        }
        is SolarEclipse -> when (event.kind) {
            EclipseKind.TOTAL -> R.string.solar_eclipse_total
            EclipseKind.ANNULAR -> R.string.solar_eclipse_annular
            else -> R.string.solar_eclipse_partial
        }
        is LunarStandstill -> if (event.major) R.string.standstill_major else R.string.standstill_minor
    },
)

/** The day of [event], with the year from next year on; for a standstill, which spans months, only month and year. */
private fun lunarEventDate(event: LunarEvent, now: ZonedDateTime, formats: UiFormats): String {
    val local = event.instant.atZone(now.zone)
    return when {
        event is LunarStandstill -> formats.monthYear(local)
        local.year != now.year -> formats.dateWithYear(local)
        else -> formats.date(local)
    }
}

/**
 * What sets [item] apart: the moon's distance; for an eclipse its course in local time, how deep it
 * goes and whether the body sets or rises during it; for a standstill the range of the moon's
 * highest points over the days of a month at [latitude].
 */
@Composable
private fun lunarEventNote(item: LocalLunarEvent, now: ZonedDateTime, latitude: Double, formats: UiFormats): String {
    fun time(instant: Instant) = instant.atZone(now.zone).let { formats.timeOfDay(it.hour * 60 + it.minute) }
    fun span(start: Instant, end: Instant) = "${time(start)}–${time(end)}"
    fun percent(fraction: Double) = (fraction * 100).roundToInt().coerceIn(0, 100)
    val parts = when (val event = item.event) {
        is Apsis -> listOf(formats.kilometers(event.distanceKm))
        is NotableFullMoon -> listOf(formats.kilometers(event.distanceKm))
        is LunarEclipse -> listOf(
            span(event.start, event.end),
            if (event.totalStart != null && event.totalEnd != null) {
                stringResource(R.string.eclipse_totality, time(event.totalStart!!), time(event.totalEnd!!))
            } else {
                stringResource(R.string.lunar_eclipse_in_umbra, percent(event.umbralMagnitude))
            },
        )
        is SolarEclipse -> listOfNotNull(
            span(event.start, event.end),
            stringResource(R.string.solar_eclipse_covered, percent(event.obscuration)),
            if (event.centralStart != null && event.centralEnd != null) {
                stringResource(
                    if (event.kind == EclipseKind.TOTAL) R.string.eclipse_totality else R.string.eclipse_annularity,
                    time(event.centralStart!!),
                    time(event.centralEnd!!),
                )
            } else {
                null
            },
        )
        is LunarStandstill -> {
            // The highest point of a day, as the moon's declination swings by ± the standstill's.
            val heights = listOf(event.declinationDeg, -event.declinationDeg).map { 90.0 - abs(latitude - it) }
            listOf(stringResource(R.string.standstill_heights, formats.wholeDegrees(heights.min()), formats.wholeDegrees(heights.max())))
        }
    }
    val belowHorizon = when {
        item.visibility != Visibility.PART -> null
        item.event is SolarEclipse -> stringResource(R.string.sun_partly_below_horizon)
        else -> stringResource(R.string.moon_partly_below_horizon)
    }
    return (parts + listOfNotNull(belowHorizon)).joinToString(" · ")
}

/** Sun events: a square slice of the ring, so the horizon at its bottom edge stands out. */
private val SKY_SLICE_SHAPE = RoundedCornerShape(3.dp)

/**
 * How high the slices reach above the horizon, the same for every widget, so the list always shows
 * twilight's colors at the horizon about equally large.
 */
private const val SKY_SLICE_TOP_DEG = 30.0
private val SKY_SLICE_SIZE = 24.dp

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
