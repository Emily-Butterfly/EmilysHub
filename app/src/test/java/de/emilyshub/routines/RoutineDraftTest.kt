package de.emilyshub.routines

import de.emilyshub.data.InputText
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
        assertEquals(80, InputText.lengthOf("🌱".repeat(80)))
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

    @Test
    fun `a routine reminds again up to five times, or not at all`() {
        fun times(count: Int) = draft(followUps = FollowUps(count = count)).problems

        assertEquals(setOf(DraftProblem.FollowUpCountOutOfRange), times(-1))
        assertEquals(emptySet<DraftProblem>(), times(0))
        assertEquals(emptySet<DraftProblem>(), times(5))
        assertEquals(setOf(DraftProblem.FollowUpCountOutOfRange), times(6))
    }

    @Test
    fun `follow-ups come from 5 to 120 minutes apart`() {
        fun every(minutes: Int) = draft(followUps = FollowUps(count = 3, everyMinutes = minutes)).problems

        assertEquals(setOf(DraftProblem.FollowUpIntervalOutOfRange), every(4))
        assertEquals(emptySet<DraftProblem>(), every(5))
        assertEquals(emptySet<DraftProblem>(), every(120))
        assertEquals(setOf(DraftProblem.FollowUpIntervalOutOfRange), every(121))
    }

    @Test
    fun `without follow-ups, how far apart they would come does not matter and goes back to the default`() {
        val none = draft(followUps = FollowUps(count = 0, everyMinutes = 0))

        assertEquals(emptySet<DraftProblem>(), none.problems)
        assertEquals(FollowUps(count = 0, everyMinutes = 30), none.cleaned().followUps)
    }

    @Test
    fun `a time window lasts from 1 to 24 hours`() {
        fun hours(hours: Int) = draft(window = TimeWindow(hours = hours)).problems

        assertEquals(setOf(DraftProblem.WindowOutOfRange), hours(0))
        assertEquals(emptySet<DraftProblem>(), hours(1))
        assertEquals(emptySet<DraftProblem>(), hours(24))
        assertEquals(setOf(DraftProblem.WindowOutOfRange), hours(25))
    }

    @Test
    fun `until midnight, how many hours does not matter and goes back to the default`() {
        val untilMidnight = draft(window = TimeWindow(hours = 0, untilMidnight = true))

        assertEquals(emptySet<DraftProblem>(), untilMidnight.problems)
        assertEquals(TimeWindow(hours = 4, untilMidnight = true), untilMidnight.cleaned().window)
    }

    @Test
    fun `checklist items lose surrounding spaces and line breaks, and empty ones are left out`() {
        val typed = listOf(
            ChecklistDraftItem(text = "  Zahnseide\n "), ChecklistDraftItem(text = " "), ChecklistDraftItem(7, "Spülen"),
        )
        val draft = draft(checklist = typed)

        assertEquals(emptySet<DraftProblem>(), draft.problems)
        assertEquals(listOf(ChecklistDraftItem(text = "Zahnseide"), ChecklistDraftItem(7, "Spülen")), draft.cleaned().checklist)
    }

    @Test
    fun `a checklist has up to 20 items of up to 80 characters each`() {
        val twenty = (1..20).map { ChecklistDraftItem(text = "Punkt $it") }

        assertEquals(emptySet<DraftProblem>(), draft(checklist = twenty).problems)
        val tooMany = twenty + ChecklistDraftItem(text = "einer zu viel")
        assertEquals(setOf(DraftProblem.ChecklistTooLong), draft(checklist = tooMany).problems)
        val tooLong = ChecklistDraftItem(text = "a".repeat(81))
        assertEquals(setOf(DraftProblem.ChecklistItemTooLong), draft(checklist = listOf(tooLong)).problems)
    }

    private fun draft(
        name: String = "Zähne putzen",
        description: String = "",
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        repetition: Repetition = Repetition.Daily,
        followUps: FollowUps = FollowUps(),
        window: TimeWindow = TimeWindow(),
        checklist: List<ChecklistDraftItem> = emptyList(),
    ) = RoutineDraft(name, description, times, repetition, followUps, window = window, checklist = checklist)
}
