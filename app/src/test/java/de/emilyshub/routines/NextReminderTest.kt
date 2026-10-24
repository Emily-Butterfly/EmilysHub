package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NextReminderTest {

    // Friday, 23 October 2026, 07:00 in Berlin.
    private val now = berlin("2026-10-23T07:00")
    private val today = LocalDate.parse("2026-10-23")

    @Test
    fun `the next reminder is at the routine's next time today`() {
        assertEquals(berlin("2026-10-23T08:00"), routine("08:00", "20:00").nextReminder(planned = emptyList(), now, BERLIN))
    }

    @Test
    fun `after its last time of the day, the next reminder is at its first time tomorrow`() {
        val routine = routine("08:00", "20:00")

        assertEquals(berlin("2026-10-24T08:00"), routine.nextReminder(emptyList(), berlin("2026-10-23T21:00"), BERLIN))
    }

    @Test
    fun `an occurrence ticked off or skipped ahead of its time is not reminded of`() {
        val planned = listOf(
            occurrence(today, "08:00", OccurrenceStatus.Done),
            occurrence(today, "12:00", OccurrenceStatus.Skipped),
            occurrence(today, "20:00"),
        )

        assertEquals(berlin("2026-10-23T20:00"), routine("08:00", "12:00", "20:00").nextReminder(planned, now, BERLIN))
    }

    @Test
    fun `an occurrence put off is reminded of again when it was put off to`() {
        val planned = listOf(occurrence(today, "08:00", remindAt = berlin("2026-10-23T08:10")), occurrence(today, "20:00"))

        val reminder = routine("08:00", "20:00").nextReminder(planned, berlin("2026-10-23T08:05"), BERLIN)

        assertEquals(berlin("2026-10-23T08:10"), reminder)
    }

    @Test
    fun `an occurrence put off past midnight is reminded of the next day`() {
        val yesterday = today.minusDays(1)
        val planned = listOf(occurrence(yesterday, "23:55", remindAt = berlin("2026-10-23T00:05")))

        assertEquals(berlin("2026-10-23T00:05"), routine("23:55").nextReminder(planned, berlin("2026-10-23T00:01"), BERLIN))
    }

    @Test
    fun `a time already past today that was not planned is not reminded of any more`() {
        val routine = routine("06:00", "20:00", plannedFrom = now)

        assertEquals(berlin("2026-10-23T20:00"), routine.nextReminder(emptyList(), now, BERLIN))
        val evening = occurrence(today, "20:00", OccurrenceStatus.Done)
        assertEquals(berlin("2026-10-24T06:00"), routine.nextReminder(listOf(evening), now, BERLIN))
    }

    @Test
    fun `on certain weekdays, the next reminder comes on the next of them, also in the next month`() {
        val mondays = routine("08:00", repetition = Repetition.Weekdays(setOf(DayOfWeek.MONDAY)))

        assertEquals(berlin("2026-11-02T08:00"), mondays.nextReminder(emptyList(), berlin("2026-10-31T10:00"), BERLIN))
    }

    @Test
    fun `every X days, the next reminder keeps the rhythm across the year change`() {
        val everyThreeDays = routine("08:00", repetition = Repetition.EveryNDays(days = 3, from = LocalDate.parse("2026-12-29")))

        assertEquals(berlin("2027-01-04T08:00"), everyThreeDays.nextReminder(emptyList(), berlin("2027-01-01T09:00"), BERLIN))
    }

    @Test
    fun `a one-time routine is reminded of on its date, and after that no more`() {
        val once = routine("09:30", repetition = Repetition.Once(LocalDate.parse("2026-10-25")))

        assertEquals(berlin("2026-10-25T09:30"), once.nextReminder(emptyList(), now, BERLIN))
        assertEquals(null, once.nextReminder(emptyList(), berlin("2026-10-25T10:00"), BERLIN))
        val done = occurrence(LocalDate.parse("2026-10-25"), "09:30", OccurrenceStatus.Done)
        assertEquals(null, once.nextReminder(listOf(done), now, BERLIN))
    }

    @Test
    fun `on the night the clocks spring forward, a time in the skipped hour is reminded of right after the jump`() {
        val pills = routine("02:30")

        assertEquals(Instant.parse("2027-03-28T01:30:00Z"), pills.nextReminder(emptyList(), berlin("2027-03-28T00:00"), BERLIN))
    }

    @Test
    fun `across the night the clocks fall back, the next reminder is at the same time on the clock`() {
        val vitamins = routine("08:00")

        // 08:00 in winter time is 07:00 UTC; the day before it was 06:00 UTC.
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), vitamins.nextReminder(emptyList(), berlin("2026-10-24T09:00"), BERLIN))
    }

    /** Without follow-ups unless given [followUps], so that the tests above are about the schedule alone. */
    @Test
    fun `an occurrence left open is reminded of again every 30 minutes, three times, and then not any more`() {
        val routine = routine("08:00", "20:00", followUps = FollowUps())
        val planned = listOf(occurrence(today, "08:00"), occurrence(today, "20:00"))

        val reminders = generateSequence(now) { routine.nextReminder(planned, it, BERLIN) }.drop(1).take(6).toList()

        assertEquals(listOf("08:00", "08:30", "09:00", "09:30", "20:00", "20:30").map(::todayAt), reminders)
    }

    @Test
    fun `a routine's own follow-ups come as often and as far apart as it says`() {
        val routine = routine("08:00", followUps = FollowUps(count = 5, everyMinutes = 10))
        val planned = listOf(occurrence(today, "08:00"))

        val reminders = generateSequence(now) { routine.nextReminder(planned, it, BERLIN) }.drop(1).take(7).toList()

        val then = listOf("08:00", "08:10", "08:20", "08:30", "08:40", "08:50").map(::todayAt)
        assertEquals(then + berlin("2026-10-24T08:00"), reminders)
    }

    @Test
    fun `an occurrence done or skipped after its time is not reminded of again`() {
        val routine = routine("08:00", "12:00", "20:00", followUps = FollowUps())
        val planned = listOf(
            occurrence(today, "08:00", OccurrenceStatus.Done),
            occurrence(today, "12:00", OccurrenceStatus.Skipped),
            occurrence(today, "20:00"),
        )

        assertEquals(todayAt("20:00"), routine.nextReminder(planned, todayAt("08:05"), BERLIN))
        assertEquals(todayAt("20:00"), routine.nextReminder(planned, todayAt("12:05"), BERLIN))
    }

    @Test
    fun `the follow-ups of an occurrence put off count from when it was put off to`() {
        val routine = routine("08:00", followUps = FollowUps())
        val planned = listOf(occurrence(today, "08:00", remindAt = todayAt("08:40")))

        assertEquals(todayAt("08:40"), routine.nextReminder(planned, todayAt("08:05"), BERLIN))
        assertEquals(todayAt("09:10"), routine.nextReminder(planned, todayAt("08:40"), BERLIN))
    }

    @Test
    fun `follow-ups go on past midnight`() {
        val routine = routine("23:50", followUps = FollowUps())
        val planned = listOf(occurrence(today.minusDays(1), "23:50"), occurrence(today, "23:50"))

        assertEquals(todayAt("00:20"), routine.nextReminder(planned, todayAt("00:00"), BERLIN))
    }

    @Test
    fun `a routine with no follow-ups reminds once`() {
        val routine = routine("08:00", followUps = FollowUps(count = 0))
        val planned = listOf(occurrence(today, "08:00"))

        assertEquals(berlin("2026-10-24T08:00"), routine.nextReminder(planned, todayAt("08:00"), BERLIN))
    }

    @Test
    fun `no reminder comes once the occurrence's time window has ended, no follow-up and nothing put off`() {
        val routine = routine("08:00", followUps = FollowUps(count = 5, everyMinutes = 30), window = TimeWindow(hours = 2))
        val planned = listOf(occurrence(today, "08:00"))

        val reminders = generateSequence(now) { routine.nextReminder(planned, it, BERLIN) }.drop(1).take(5).toList()

        assertEquals(listOf("08:00", "08:30", "09:00", "09:30").map(::todayAt) + berlin("2026-10-24T08:00"), reminders)
        val putOffTooLate = listOf(occurrence(today, "08:00", remindAt = todayAt("10:10")))
        assertEquals(berlin("2026-10-24T08:00"), routine.nextReminder(putOffTooLate, todayAt("09:00"), BERLIN))
    }

    @Test
    fun `on a day not planned yet, reminders end with the window too`() {
        val routine = routine("23:00", followUps = FollowUps(count = 3, everyMinutes = 30), window = TimeWindow(untilMidnight = true))

        assertEquals(todayAt("23:30"), routine.nextReminder(emptyList(), todayAt("23:00"), BERLIN))
        assertEquals(berlin("2026-10-24T23:00"), routine.nextReminder(emptyList(), todayAt("23:30"), BERLIN))
    }

    @Test
    fun `the next time window to end is that of an occurrence still open`() {
        val routine = routine("08:00", "22:00", window = TimeWindow(hours = 3))
        val planned = listOf(
            occurrence(today.minusDays(1), "22:00"), // open until 01:00 today
            occurrence(today, "08:00", OccurrenceStatus.Done),
            occurrence(today, "22:00"),
        )

        assertEquals("the day before's, after midnight", todayAt("01:00"), routine.nextWindowEnd(planned, todayAt("00:30"), BERLIN))
        assertEquals("not that of one done", berlin("2026-10-24T01:00"), routine.nextWindowEnd(planned, todayAt("07:00"), BERLIN))
        assertEquals(null, routine.nextWindowEnd(planned, berlin("2026-10-24T01:00"), BERLIN))
    }

    /** [time] ("08:30") today in Berlin. */
    private fun todayAt(time: String) = berlin("${today}T$time")

    private fun routine(
        vararg times: String,
        repetition: Repetition = Repetition.Daily,
        plannedFrom: Instant = berlin("2026-10-01T00:00"),
        followUps: FollowUps = FollowUps(count = 0),
        window: TimeWindow = TimeWindow(),
    ) = Routine(
        id = ROUTINE,
        name = "Tabletten",
        description = "",
        times = times.map(LocalTime::parse),
        repetition = repetition,
        plannedFrom = plannedFrom,
        followUps = followUps,
        window = window,
    )

    private fun occurrence(
        date: LocalDate,
        time: String,
        status: OccurrenceStatus = OccurrenceStatus.Open,
        remindAt: Instant? = null,
    ) = Occurrence(routineId = ROUTINE, date = date, time = LocalTime.parse(time), status = status, remindAt = remindAt)

    private companion object {
        const val ROUTINE = 1L
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

        fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()
    }
}
