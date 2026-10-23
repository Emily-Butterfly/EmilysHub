package de.emilyshub.routines

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// Routines run on the local wall clock, wherever the phone is: 08:00 stays 08:00 after a flight and
// across clock changes. So days are counted as local dates, never as 24-hour steps.

/** The local date at [now]: the day changes at midnight in [zone]. */
fun today(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate() // LocalDate.ofInstant needs API 34

/**
 * When a routine with [time] is due on [date]. A time skipped when the clocks spring forward is due
 * right after the jump (02:30 becomes 03:30); a time that happens twice when they fall back is due
 * the first time round.
 */
fun dueAt(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime = ZonedDateTime.of(date, time, zone)
