package de.emilyshub.ui

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor

private val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** A time of day as German clocks show it: 07:30, 20:00. */
fun TemporalAccessor.clockTime(): String = CLOCK_TIME.format(this)
