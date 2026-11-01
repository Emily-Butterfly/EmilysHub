package de.emilyshub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.emilyshub.Day
import java.time.Instant
import java.time.LocalDate

/**
 * The time now by [day], kept current while the screen shows: just after each full minute, and as the phone's clock
 * changes; read anew on return to the app, as the waits stand still while the phone sleeps. For what changes as time
 * goes by, such as a to-do turning overdue.
 */
@Composable
fun rememberNow(day: Day): Instant = remember(day) { day.minutes() }.collectAsStateWithLifecycle(initialValue = day.now()).value

/** The day it is by [day], kept current while the screen shows: at midnight, and as the phone's clock or time zone changes. */
@Composable
fun rememberToday(day: Day): LocalDate =
    remember(day) { day.dates() }.collectAsStateWithLifecycle(initialValue = day.today()).value
