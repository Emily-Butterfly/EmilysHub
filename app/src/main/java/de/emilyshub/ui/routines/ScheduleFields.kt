package de.emilyshub.ui.routines

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.emilyshub.R
import de.emilyshub.reminders.fullScreenAlarmsAllowed
import de.emilyshub.routines.FollowUps
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.TimeWindow
import de.emilyshub.ui.SectionTitle
import de.emilyshub.ui.SystemSettings
import de.emilyshub.ui.clockTime
import de.emilyshub.ui.fields.DateDialog
import de.emilyshub.ui.fields.DateValue
import de.emilyshub.ui.fields.TimeDialog
import de.emilyshub.ui.openSettings
import de.emilyshub.ui.reminders.FullScreenAlarmPermission
import java.io.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Which time the time dialog sets. Serializable, so that an open dialog survives rotation and the lock. */
private sealed interface TimeChoice : Serializable {
    data object New : TimeChoice

    data class Change(val time: LocalTime) : TimeChoice
}

/** The times of day: tapping one changes it; times can be added and taken away, but one always stays. */
@Composable
fun TimesFields(times: List<LocalTime>, onChange: (List<LocalTime>) -> Unit) {
    var choice by rememberSaveable { mutableStateOf<TimeChoice?>(null) }
    Column {
        SectionTitle(stringResource(R.string.routine_times))
        times.forEach { time ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = stringResource(R.string.field_time_change),
                        role = Role.Button,
                        onClick = { choice = TimeChoice.Change(time) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = time.clockTime(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                )
                if (times.size > 1) {
                    IconButton(onClick = { onChange(times - time) }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.routine_time_remove, time.clockTime()),
                        )
                    }
                }
            }
        }
        if (times.size < RoutineDraft.TIMES_MAX) {
            TextButton(onClick = { choice = TimeChoice.New }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.routine_time_add))
            }
        }
    }

    val current = choice ?: return
    TimeDialog(
        initial = when (current) {
            TimeChoice.New -> times.last().plusHours(1)
            is TimeChoice.Change -> current.time
        },
        onConfirm = { picked ->
            val others = if (current is TimeChoice.Change) times - current.time else times
            onChange((others + picked).distinct().sorted())
            choice = null
        },
        onDismiss = { choice = null },
    )
}

/**
 * Daily, on certain weekdays, every X days from a date, or once on a date. Switching to one starts it from
 * [today]; [goneBy] says that a single date and its times are over.
 */
@Composable
fun RepetitionFields(repetition: Repetition, today: LocalDate, goneBy: Boolean, onChange: (Repetition) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var intervalText by rememberSaveable {
        mutableStateOf(((repetition as? Repetition.EveryNDays)?.days ?: DEFAULT_INTERVAL).toString())
    }
    var choosingDate by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.selectableGroup()) {
        SectionTitle(stringResource(R.string.routine_repeat))
        RepetitionOption(stringResource(R.string.routine_repeat_daily), selected = repetition == Repetition.Daily) {
            onChange(Repetition.Daily)
        }
        RepetitionOption(stringResource(R.string.routine_repeat_weekdays), selected = repetition is Repetition.Weekdays) {
            if (repetition !is Repetition.Weekdays) onChange(Repetition.Weekdays(WORKDAYS))
        }
        if (repetition is Repetition.Weekdays) {
            WeekdayChips(repetition.days, onChange = { onChange(Repetition.Weekdays(it)) })
        }
        RepetitionOption(stringResource(R.string.routine_repeat_every_n_days), selected = repetition is Repetition.EveryNDays) {
            if (repetition !is Repetition.EveryNDays) {
                onChange(Repetition.EveryNDays(days = intervalText.toIntOrNull() ?: 0, from = today))
            }
        }
        if (repetition is Repetition.EveryNDays) {
            val label = stringResource(R.string.routine_interval)
            NumberField(intervalText, repetition.days, RoutineDraft.INTERVAL_DAYS, label) { text, days ->
                intervalText = text
                onChange(repetition.copy(days = days))
            }
            DateValue(stringResource(R.string.routine_starting), repetition.from, onClick = { choosingDate = true })
        }
        RepetitionOption(stringResource(R.string.routine_repeat_once), selected = repetition is Repetition.Once) {
            if (repetition !is Repetition.Once) onChange(Repetition.Once(today))
        }
        if (repetition is Repetition.Once) {
            DateValue(stringResource(R.string.routine_on), repetition.date, onClick = { choosingDate = true })
            if (goneBy) Problem(stringResource(R.string.routine_once_gone_by))
        }
    }

    if (choosingDate) {
        val close = { choosingDate = false }
        when (repetition) {
            is Repetition.EveryNDays -> DateDialog(
                initial = repetition.from,
                earliest = null,
                onConfirm = {
                    onChange(repetition.copy(from = it))
                    close()
                },
                onDismiss = close,
            )
            // A date gone by would never come due.
            is Repetition.Once -> DateDialog(
                initial = repetition.date,
                earliest = today,
                onConfirm = {
                    onChange(Repetition.Once(it))
                    close()
                },
                onDismiss = close,
            )
            Repetition.Daily, is Repetition.Weekdays -> close()
        }
    }
}

/** How often a reminder left unanswered comes again, never or up to five times, and while it does, how many minutes apart. */
@Composable
fun FollowUpFields(followUps: FollowUps, onChange: (FollowUps) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var minutesText by rememberSaveable { mutableStateOf(followUps.everyMinutes.toString()) }
    val counts = FollowUps.COUNTS.toList()

    Column {
        SectionTitle(stringResource(R.string.routine_follow_ups))
        Text(stringResource(R.string.routine_follow_ups_how_often), style = MaterialTheme.typography.bodyMedium)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            counts.forEachIndexed { index, count ->
                SegmentedButton(
                    selected = followUps.count == count,
                    onClick = { onChange(followUps.copy(count = count)) },
                    shape = SegmentedButtonDefaults.itemShape(index, counts.size),
                    // Without the check mark, so that six fit side by side.
                    icon = {},
                    label = { Text(if (count == 0) stringResource(R.string.routine_follow_ups_never) else count.toString()) },
                )
            }
        }
        if (followUps.count > 0) {
            val label = stringResource(R.string.routine_follow_up_minutes)
            NumberField(minutesText, followUps.everyMinutes, FollowUps.MINUTES, label) { text, minutes ->
                minutesText = text
                onChange(followUps.copy(everyMinutes = minutes))
            }
        }
    }
}

/**
 * Whether the routine's reminders fill the screen like an alarm clock (SPEC.md §3). Turned on, it asks for
 * Android's permission where that is missing; declined, it stays on for when Android allows it, and says
 * that it does not work until then.
 */
@Composable
fun FullScreenField(fullScreen: Boolean, onChange: (Boolean) -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Again on every return, from Android's settings for one.
    var allowed by remember { mutableStateOf(context.fullScreenAlarmsAllowed()) }
    LifecycleResumeEffect(context) {
        allowed = context.fullScreenAlarmsAllowed()
        onPauseOrDispose {}
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = fullScreen,
                role = Role.Switch,
                onValueChange = { on ->
                    onChange(on)
                    asking = on
                },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.routine_full_screen), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.routine_full_screen_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = fullScreen, onCheckedChange = null)
    }
    if (fullScreen && !allowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Problem(stringResource(R.string.routine_full_screen_not_allowed))
        TextButton(onClick = { context.openSettings(SystemSettings.fullScreenAlarms(context)) }) {
            Text(stringResource(R.string.routine_full_screen_allow))
        }
    }
    if (asking) {
        FullScreenAlarmPermission(
            onFinished = {
                asking = false
                allowed = context.fullScreenAlarmsAllowed()
            },
        )
    }
}

/**
 * How long an occurrence can be caught up on before it is missed (SPEC.md §2): a number of hours, or until
 * midnight, when there are no hours to choose.
 */
@Composable
fun TimeWindowFields(window: TimeWindow, onChange: (TimeWindow) -> Unit) {
    // As typed, so that the field can be empty for a moment while the number is retyped.
    var hoursText by rememberSaveable { mutableStateOf(window.hours.toString()) }

    Column {
        SectionTitle(stringResource(R.string.routine_window))
        Text(stringResource(R.string.routine_window_summary), style = MaterialTheme.typography.bodyMedium)
        if (!window.untilMidnight) {
            NumberField(hoursText, window.hours, TimeWindow.HOURS, stringResource(R.string.routine_window_hours)) { text, hours ->
                hoursText = text
                onChange(window.copy(hours = hours))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = window.untilMidnight,
                    role = Role.Checkbox,
                    onValueChange = { onChange(window.copy(untilMidnight = it)) },
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = window.untilMidnight, onCheckedChange = null)
            Text(
                stringResource(R.string.routine_window_until_midnight),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/**
 * A whole number from [range]: [text] is kept as typed, so that the field can be empty for a moment while
 * the [number] is retyped; empty counts as 0. Red while [number] is out of [range].
 */
@Composable
internal fun NumberField(text: String, number: Int, range: IntRange, label: String, onChange: (String, Int) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { typed ->
            val digits = typed.filter(Char::isDigit).take(NUMBER_DIGITS)
            onChange(digits, digits.toIntOrNull() ?: 0)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = number !in range,
        supportingText = { Text(stringResource(R.string.routine_interval_range, range.first, range.last)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

/** One of a choice, with its radio button. */
@Composable
internal fun RepetitionOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

/** One chip per weekday, Monday first; a hint while none is chosen. */
@Composable
internal fun WeekdayChips(days: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    val names = stringArrayResource(R.array.weekdays_short)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in days,
                onClick = { onChange(if (day in days) days - day else days + day) },
                label = { Text(names[day.ordinal]) },
            )
        }
    }
    if (days.isEmpty()) Problem(stringResource(R.string.routine_weekdays_missing))
}

/** What keeps the routine or to-do from being saved, under the field it is about. */
@Composable
internal fun Problem(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private val WORKDAYS: Set<DayOfWeek> = DayOfWeek.entries.take(5).toSet()
private const val DEFAULT_INTERVAL = 2
private const val NUMBER_DIGITS = 3
