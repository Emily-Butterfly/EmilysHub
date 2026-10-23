package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RepetitionTest {

    @Test
    fun `on certain weekdays, it is due on those days only, also across the change of month`() {
        val mondayWednesdayFriday = Repetition.Weekdays(setOf(MONDAY, WEDNESDAY, FRIDAY))

        assertEquals(
            listOf("2026-10-30", "2026-11-02", "2026-11-04"),
            dueDays(mondayWednesdayFriday, from = "2026-10-29", to = "2026-11-04"),
        )
    }

    @Test
    fun `every X days, it is due on the start date and every X days after, never before it`() {
        val everyThreeDays = Repetition.EveryNDays(days = 3, from = LocalDate.parse("2026-10-23"))

        assertEquals(
            listOf("2026-10-23", "2026-10-26", "2026-10-29"),
            dueDays(everyThreeDays, from = "2026-10-17", to = "2026-10-30"),
        )
    }

    @Test
    fun `every X days carries on across the year change`() {
        val weekly = Repetition.EveryNDays(days = 7, from = LocalDate.parse("2026-12-20"))

        assertEquals(
            listOf("2026-12-20", "2026-12-27", "2027-01-03", "2027-01-10"),
            dueDays(weekly, from = "2026-12-20", to = "2027-01-10"),
        )
    }

    @Test
    fun `every X days counts the leap day as a day`() {
        val everyTwoDays = Repetition.EveryNDays(days = 2, from = LocalDate.parse("2028-02-27"))

        assertEquals(
            listOf("2028-02-27", "2028-02-29", "2028-03-02"),
            dueDays(everyTwoDays, from = "2028-02-27", to = "2028-03-02"),
        )
    }

    @Test
    fun `every X days goes from the end of one month into the next, in a year without a leap day too`() {
        val everyTwoDays = Repetition.EveryNDays(days = 2, from = LocalDate.parse("2027-02-27"))

        assertEquals(
            listOf("2027-02-27", "2027-03-01", "2027-03-03"),
            dueDays(everyTwoDays, from = "2027-02-27", to = "2027-03-03"),
        )
    }

    @Test
    fun `every X days goes by calendar days across the change to winter time, at the same time on the clock`() {
        val everyTwoDays = Repetition.EveryNDays(days = 2, from = LocalDate.parse("2026-10-23"))
        val eight = LocalTime.of(8, 0)

        val due = dueDays(everyTwoDays, from = "2026-10-23", to = "2026-10-27").map { dueAt(LocalDate.parse(it), eight, BERLIN) }

        assertEquals(
            listOf("2026-10-23T08:00", "2026-10-25T08:00", "2026-10-27T08:00"),
            due.map { it.toLocalDateTime().toString() },
        )
        // The night the clocks go back has an extra hour.
        assertEquals(Duration.ofHours(49), Duration.between(due[0], due[1]))
    }

    @Test
    fun `on the weekday the clocks spring forward, a time in the skipped hour is due right after the jump`() {
        val sundays = Repetition.Weekdays(setOf(SUNDAY))
        val springForward = LocalDate.parse("2027-03-28")

        assertEquals(listOf("2027-03-28"), dueDays(sundays, from = "2027-03-22", to = "2027-03-28"))
        assertEquals(LocalTime.of(3, 30), dueAt(springForward, LocalTime.of(2, 30), BERLIN).toLocalTime())
    }

    @Test
    fun `once, it is due on its date only`() {
        val once = Repetition.Once(LocalDate.parse("2026-12-31"))

        assertEquals(listOf("2026-12-31"), dueDays(once, from = "2026-12-01", to = "2027-01-31"))
    }

    @Test
    fun `daily, it is due every day, also on the leap day and across the year change`() {
        assertEquals(
            listOf("2028-02-28", "2028-02-29", "2028-03-01"),
            dueDays(Repetition.Daily, from = "2028-02-28", to = "2028-03-01"),
        )
        assertEquals(listOf("2026-12-31", "2027-01-01"), dueDays(Repetition.Daily, from = "2026-12-31", to = "2027-01-01"))
    }

    /** The days from [from] to [to] on which [repetition] is due. */
    private fun dueDays(repetition: Repetition, from: String, to: String): List<String> =
        generateSequence(LocalDate.parse(from)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.parse(to)) }
            .filter(repetition::isDueOn)
            .map(LocalDate::toString)
            .toList()

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
