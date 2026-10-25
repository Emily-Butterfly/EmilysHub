package de.emilyshub.ui

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

private val CLOCK_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** A time of day as German clocks show it: 07:30, 20:00. */
fun TemporalAccessor.clockTime(): String = CLOCK_TIME.format(this)

private val NUMERIC_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** A date as Germans write it in short: 30.10.2026. */
fun LocalDate.numericDate(): String = NUMERIC_DATE.format(this)

private val DAY_AND_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)

/** A day of the year, as Germans say it: 21. Oktober. */
fun LocalDate.dayAndMonth(): String = DAY_AND_MONTH.format(this)

private val MONTH_AND_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.GERMAN)

/** A month, as a calendar names it: Oktober 2026. */
fun YearMonth.monthAndYear(): String = MONTH_AND_YEAR.format(this)
