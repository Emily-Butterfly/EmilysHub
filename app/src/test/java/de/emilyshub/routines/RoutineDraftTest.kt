package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class RoutineDraftTest {

    @Test
    fun `a routine needs a name`() {
        assertEquals(setOf(DraftProblem.NameMissing), draft(name = " \n ").problems)
    }

    @Test
    fun `the name is kept on one line and both texts lose surrounding spaces`() {
        val cleaned = draft(name = "  Zähne\n putzen\t", description = "\n Abends auch Zahnseide.\nNicht vergessen!  ").cleaned()

        assertEquals("Zähne putzen", cleaned.name)
        assertEquals("Abends auch Zahnseide.\nNicht vergessen!", cleaned.description)
    }

    @Test
    fun `a name fits up to 80 characters, not counting surrounding spaces`() {
        assertEquals(emptySet<DraftProblem>(), draft(name = "  " + "a".repeat(80) + "  ").problems)
        assertEquals(setOf(DraftProblem.NameTooLong), draft(name = "a".repeat(81)).problems)
    }

    @Test
    fun `a name of only invisible characters counts as missing`() {
        assertEquals(setOf(DraftProblem.NameMissing), draft(name = "​⁠‍").problems)
    }

    @Test
    fun `an emoji counts as one character`() {
        assertEquals(emptySet<DraftProblem>(), draft(name = "🌱".repeat(80)).problems)
        assertEquals(80, RoutineDraft.lengthOf("🌱".repeat(80)))
    }

    @Test
    fun `the description is optional and fits up to 1000 characters`() {
        assertEquals(emptySet<DraftProblem>(), draft(description = "").problems)
        assertEquals(emptySet<DraftProblem>(), draft(description = "a".repeat(1000)).problems)
        assertEquals(setOf(DraftProblem.DescriptionTooLong), draft(description = "a".repeat(1001)).problems)
    }

    @Test
    fun `a routine needs at least one time`() {
        assertEquals(setOf(DraftProblem.TimesMissing), draft(times = emptyList()).problems)
    }

    @Test
    fun `the times are kept in the order of the day, each only once`() {
        val cleaned = draft(times = listOf("20:00", "08:00", "12:30", "08:00").map(LocalTime::parse)).cleaned()

        assertEquals(listOf("08:00", "12:30", "20:00"), cleaned.times.map(LocalTime::toString))
    }

    @Test
    fun `a routine fits up to 24 different times a day`() {
        val hourly = (0..23).map { LocalTime.of(it, 0) }

        assertEquals(emptySet<DraftProblem>(), draft(times = hourly + hourly).problems)
        assertEquals(setOf(DraftProblem.TooManyTimes), draft(times = hourly + LocalTime.of(12, 30)).problems)
    }

    @Test
    fun `certain weekdays means at least one of them`() {
        assertEquals(setOf(DraftProblem.WeekdaysMissing), draft(repetition = Repetition.Weekdays(emptySet())).problems)
        assertEquals(emptySet<DraftProblem>(), draft(repetition = Repetition.Weekdays(setOf(DayOfWeek.SUNDAY))).problems)
    }

    @Test
    fun `every X days takes from 2 to 365 days`() {
        fun every(days: Int) = draft(repetition = Repetition.EveryNDays(days, LocalDate.parse("2026-10-23"))).problems

        assertEquals(setOf(DraftProblem.IntervalOutOfRange), every(1))
        assertEquals(emptySet<DraftProblem>(), every(2))
        assertEquals(emptySet<DraftProblem>(), every(365))
        assertEquals(setOf(DraftProblem.IntervalOutOfRange), every(366))
    }

    private fun draft(
        name: String = "Zähne putzen",
        description: String = "",
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        repetition: Repetition = Repetition.Daily,
    ) = RoutineDraft(name = name, description = description, times = times, repetition = repetition)
}
