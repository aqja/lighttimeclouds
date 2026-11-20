package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.data.roundCoordinate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The saved places to choose the widget's place from, with buttons to edit or delete each and to
 * add a new one. Places are only ever deleted from this list on purpose, never as a side effect of
 * removing a widget. Choosing a place is part of the widget settings (saved with "Save"); saving a
 * place's details applies to all widgets using it right away.
 */
@Composable
internal fun LocationSection(settings: WidgetSettings, viewModel: ConfigViewModel, expanded: Boolean, onToggle: () -> Unit) {
    val places by viewModel.places.collectAsState()
    val placesInUse by viewModel.placesInUse.collectAsState()
    var editing by remember { mutableStateOf<Place?>(null) }
    var deleting by remember { mutableStateOf<Place?>(null) }
    // A place in use by a saved widget, the template, or this (unsaved) selection must stay, as must the last one.
    fun isInUse(place: Place) = place.id in placesInUse || place.id == settings.place.id
    fun canDelete(place: Place) = places.size > 1 && !isInUse(place)
    val formats = rememberUiFormats()

    Section(stringResource(R.string.section_location), summary = settings.place.name, expanded = expanded, onToggle = onToggle) {
        Column(Modifier.selectableGroup()) {
            for (place in places) {
                PlaceRow(
                    place = place,
                    details = placeDetails(place, formats),
                    selected = place.id == settings.place.id,
                    inUse = isInUse(place),
                    canDelete = canDelete(place),
                    onSelect = { viewModel.update { it.copy(place = place) } },
                    onEdit = { editing = place },
                    onDelete = { deleting = place },
                )
            }
        }
        TextButton(onClick = { editing = Place.new() }) { Text(stringResource(R.string.place_new)) }
    }

    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.place_delete_title)) },
            text = { Text(stringResource(R.string.place_delete_text, place.name)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePlace(place)
                    deleting = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    editing?.let { place ->
        val isNew = places.none { it.id == place.id }
        PlaceEditorDialog(
            place = place,
            isNew = isNew,
            onSave = {
                viewModel.savePlace(it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** One saved place to choose with its radio button, and buttons to edit or delete it. */
@Composable
private fun PlaceRow(
    place: Place,
    details: String,
    selected: Boolean,
    inUse: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(place.name, style = MaterialTheme.typography.bodyLarge)
            Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (inUse) Text(stringResource(R.string.place_in_use), style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit))
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

@Composable
internal fun PlaceEditorDialog(
    place: Place,
    isNew: Boolean,
    onSave: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(place.name) }
    var latText by remember { mutableStateOf(place.latitude.toString()) }
    var lonText by remember { mutableStateOf(place.longitude.toString()) }
    var zone by remember { mutableStateOf(place.zone) }
    var showZonePicker by remember { mutableStateOf(false) }
    val latitude = parseCoordinate(latText, LATITUDE_RANGE)
    val longitude = parseCoordinate(lonText, LONGITUDE_RANGE)
    val valid = name.isNotBlank() && latitude != null && longitude != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.place_new else R.string.place_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.place_name)) },
                    isError = name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CoordinateField(latText, stringResource(R.string.place_latitude), LATITUDE_RANGE, onValueChange = { latText = it })
                CoordinateField(lonText, stringResource(R.string.place_longitude), LONGITUDE_RANGE, onValueChange = { lonText = it })
                ListItem(
                    headlineContent = { Text(stringResource(R.string.place_time_zone)) },
                    supportingContent = { Text(zoneLabel(zone)) },
                    trailingContent = { TextButton(onClick = { showZonePicker = true }) { Text(stringResource(R.string.action_change)) } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(place.copy(name = name.trim(), latitude = latitude!!, longitude = longitude!!, zone = zone)) },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showZonePicker) {
        ZonePickerDialog(
            selected = zone,
            onSelect = {
                zone = it
                showZonePicker = false
            },
            onDismiss = { showZonePicker = false },
        )
    }
}

/**
 * Region-based time zones plus UTC, sorted by their current UTC offset and then by name. Opens
 * scrolled to [selected], so zones with smaller offsets lie above it and larger ones below.
 * Searching matches both the zone name and the offset text, e.g. "Berlin" or "+05:30".
 */
@Composable
private fun ZonePickerDialog(selected: ZoneId, onSelect: (ZoneId) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val allZones = remember(selected) { selectableZones(Instant.now(), include = selected) }
    val matches = remember(query, allZones) {
        val q = query.trim().replace(' ', '_')
        allZones.filter { zoneLabel(it).contains(q, ignoreCase = true) }
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (allZones.indexOf(selected) - ZONE_PICKER_CONTEXT_ROWS).coerceAtLeast(0),
    )
    LaunchedEffect(query) {
        if (query.isNotEmpty()) listState.scrollToItem(0)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.place_time_zone)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.zone_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp), state = listState) {
                    items(matches, key = { it.id }) { zone ->
                        ListItem(
                            leadingContent = { RadioButton(selected = zone == selected, onClick = null) },
                            headlineContent = { Text(zone.id) },
                            supportingContent = { Text("UTC" + offsetText(zone)) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onSelect(zone) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Rows shown above the selected zone when the picker opens, so it is not glued to the top edge. */
private const val ZONE_PICKER_CONTEXT_ROWS = 2

/** Prefixes of the geographic tz database zones; leaves out aliases like "Etc/GMT+5" (inverted sign) or "SystemV/...". */
private val REGION_PREFIXES = listOf(
    "Africa/", "America/", "Antarctica/", "Arctic/", "Asia/", "Atlantic/", "Australia/", "Europe/", "Indian/", "Pacific/",
)

/** Selectable zones sorted by offset at [now], then by id; [include] is kept even if it is not a region zone. */
private fun selectableZones(now: Instant, include: ZoneId): List<ZoneId> =
    (ZoneId.getAvailableZoneIds().filter { id -> id == "UTC" || REGION_PREFIXES.any { id.startsWith(it) } } + include.id)
        .distinct()
        .map { ZoneId.of(it) }
        .sortedWith(compareBy({ it.rules.getOffset(now).totalSeconds }, { it.id }))

private val LATITUDE_RANGE = -90.0..90.0
private val LONGITUDE_RANGE = -180.0..180.0

/** Accepts both "." and "," (decimal comma keyboards) and rounds as a place keeps it; null if not a number within [range]. */
private fun parseCoordinate(text: String, range: ClosedFloatingPointRange<Double>): Double? =
    text.replace(',', '.').toDoubleOrNull()?.takeIf { it in range }?.let(::roundCoordinate)

@Composable
private fun CoordinateField(
    value: String,
    label: String,
    range: ClosedFloatingPointRange<Double>,
    onValueChange: (String) -> Unit,
) {
    val isError = parseCoordinate(value, range) == null
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = if (isError) ({ Text(stringResource(R.string.place_invalid_value)) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun placeDetails(place: Place, formats: UiFormats): String =
    formats.coordinates(place.latitude, place.longitude) + " · " + zoneLabel(place.zone)

/** Zone id with its current UTC offset, e.g. "Europe/Berlin (UTC+02:00)". */
private fun zoneLabel(zone: ZoneId): String = "${zone.id} (UTC${offsetText(zone)})"

/** Current offset as "+02:00"; UTC itself as "+00:00" instead of "Z". */
private fun offsetText(zone: ZoneId): String {
    val offset = zone.rules.getOffset(Instant.now())
    return if (offset == ZoneOffset.UTC) "+00:00" else offset.id
}
