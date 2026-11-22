package com.umfluten.lighttimeclouds.config

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.delay

/** Follows the system's light/dark setting and, from Android 12 on, the wallpaper-derived system colors. */
@Composable
internal fun ConfigTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** App bar with the app name, [subtitle] and [actions], optional [tabs] below it, an optional [bottomBar] and [snackbarHost]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConfigScaffold(
    subtitle: String?,
    tabs: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.app_name))
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                )
                tabs()
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/**
 * A settings topic that opens when its title is tapped. Closed, it shows its title and a [summary]
 * of what is set, so every topic fits on one screen; topics are separated by a line.
 */
@Composable
internal fun Section(title: String, summary: String, expanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .expandable(expanded, onToggle)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!expanded && summary.isNotEmpty()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ExpandArrow(expanded)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                content()
            }
        }
        HorizontalDivider()
    }
}

/** Opens and closes on a tap, and tells accessibility services that it does. */
internal fun Modifier.expandable(expanded: Boolean, onToggle: () -> Unit): Modifier = this
    .clickable(role = Role.Button, onClick = onToggle)
    .semantics {
        if (expanded) {
            collapse { onToggle(); true }
        } else {
            expand { onToggle(); true }
        }
    }

/** The arrow of something [expandable]: down while closed, turning up as it opens. */
@Composable
internal fun ExpandArrow(expanded: Boolean) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "expand arrow")
    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(turn))
}

/** Single-choice buttons; each option pairs a value with the string resource of its label. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedChoice(options: List<Pair<T, Int>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(stringResource(label))
            }
        }
    }
}

/** Secondary text explaining the settings around it. */
@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * A value under its label, with room for a [note] below it even while empty, so that a row of them
 * keeps its height and its values stay in line.
 */
@Composable
internal fun RowScope.LabeledValue(
    label: String,
    value: String,
    note: String = "",
    weight: Float = 1f,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
) {
    Column(Modifier.weight(weight)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = fontWeight, fontStyle = fontStyle)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Heading of the control below it, inside a [Section]. */
@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
}

/** The current time in [zone], updated at every full minute. */
@Composable
internal fun rememberMinuteClock(zone: ZoneId): ZonedDateTime {
    var now by remember(zone) { mutableStateOf(ZonedDateTime.now(zone)) }
    LaunchedEffect(zone) {
        while (true) {
            delay((60 - ZonedDateTime.now().second) * 1000L)
            now = ZonedDateTime.now(zone)
        }
    }
    return now
}

/** Text color of a row in a list over time: highlighted for the current or next entry, dimmed for past ones. */
@Composable
internal fun timelineColor(highlighted: Boolean, past: Boolean): Color = when {
    highlighted -> MaterialTheme.colorScheme.primary
    past -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.onSurface
}

/** A full-width row that toggles [checked] when tapped anywhere, not just on the switch. */
@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A slider with its label on the left and the current [value] on the right above it; [steps] as in [Slider]. */
@Composable
internal fun LabeledSlider(
    label: String,
    value: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    steps: Int = 0,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Slider(value = position, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
