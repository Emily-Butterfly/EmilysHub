package de.emilyshub.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

private val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** A time of day as German clocks show it: 07:30, 20:00. */
fun TemporalAccessor.clockTime(): String = CLOCK_TIME.format(this)

private val NUMERIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** A date as Germans write it in short: 30.10.2026. */
fun LocalDate.numericDate(): String = NUMERIC_DATE.format(this)
