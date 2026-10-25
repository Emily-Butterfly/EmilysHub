package de.emilyshub.todos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/**
 * When a to-do that comes again is due next (SPEC.md §5): by plan, the next day of its rule after it was due and after
 * the day it was done, those missed in between left out; or from the day it was done.
 */
class RecurrenceTest {

    @Test
    fun `daily, it is due the day after it was due, or after it was done if that was later`() {
        assertEquals(day("2026-10-27"), Recurrence.Daily.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-26")))
        assertEquals(day("2026-10-30"), Recurrence.Daily.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-29")))
        assertEquals(day("2026-10-27"), Recurrence.Daily.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-24")))
    }

    @Test
    fun `on weekdays, it is due on the next of them after it was due and after it was done`() {
        val mondayAndThursday = Recurrence.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        // Due on Monday, 26 October 2026.
        assertEquals(day("2026-10-29"), mondayAndThursday.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-26")))
        assertEquals(day("2026-10-29"), mondayAndThursday.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-28")))
        assertEquals(
            "Done late, the Thursday missed is left out",
            day("2026-11-02"),
            mondayAndThursday.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-30")),
        )
        assertEquals("Done early", day("2026-10-29"), mondayAndThursday.nextDue(due = day("2026-10-26"), doneOn = day("2026-10-24")))
    }

    @Test
    fun `every so many days, by plan it keeps its days, and from done it counts from the day it was done`() {
        val weekly = Recurrence.EveryNDays(7)

        assertEquals(day("2026-10-26"), weekly.nextDue(due = day("2026-10-19"), doneOn = day("2026-10-19")))
        assertEquals(day("2026-10-26"), weekly.nextDue(due = day("2026-10-19"), doneOn = day("2026-10-25")))
        assertEquals(
            "Done on the day it would be due again",
            day("2026-11-02"),
            weekly.nextDue(due = day("2026-10-19"), doneOn = day("2026-10-26")),
        )
        assertEquals("Done early", day("2026-10-26"), weekly.nextDue(due = day("2026-10-19"), doneOn = day("2026-10-15")))

        val fortnightFromDone = Recurrence.EveryNDays(14, fromDone = true)
        assertEquals(day("2026-11-04"), fortnightFromDone.nextDue(due = day("2026-10-19"), doneOn = day("2026-10-21")))
    }

    @Test
    fun `monthly, it is due on its day of the month, on the last one of a shorter month, and on its day again after`() {
        val onThe31st = Recurrence.Monthly(31)

        assertEquals(day("2027-02-28"), onThe31st.nextDue(due = day("2027-01-31"), doneOn = day("2027-01-31")))
        assertEquals(day("2027-03-31"), onThe31st.nextDue(due = day("2027-02-28"), doneOn = day("2027-02-28")))
        assertEquals(day("2028-02-29"), onThe31st.nextDue(due = day("2028-01-31"), doneOn = day("2028-01-31")))

        val onThe1st = Recurrence.Monthly(1)
        assertEquals("Done late", day("2026-11-01"), onThe1st.nextDue(due = day("2026-10-01"), doneOn = day("2026-10-03")))
        assertEquals("Done early", day("2026-11-01"), onThe1st.nextDue(due = day("2026-10-01"), doneOn = day("2026-09-28")))
        assertEquals(
            "Done more than a month late",
            day("2026-12-01"),
            onThe1st.nextDue(due = day("2026-10-01"), doneOn = day("2026-11-01")),
        )

        val fromDone = Recurrence.Monthly(1, fromDone = true)
        assertEquals(day("2026-11-17"), fromDone.nextDue(due = day("2026-10-01"), doneOn = day("2026-10-17")))
    }

    @Test
    fun `yearly, it is due on its date, on 28 February when 29 February is not there`() {
        val leapDay = Recurrence.Yearly(MonthDay.of(2, 29))

        assertEquals(day("2029-02-28"), leapDay.nextDue(due = day("2028-02-29"), doneOn = day("2028-02-29")))
        assertEquals(day("2030-02-28"), leapDay.nextDue(due = day("2029-02-28"), doneOn = day("2029-02-28")))
        assertEquals(day("2032-02-29"), leapDay.nextDue(due = day("2031-02-28"), doneOn = day("2031-02-28")))

        val taxes = Recurrence.Yearly(MonthDay.of(5, 31))
        assertEquals("Done late", day("2027-05-31"), taxes.nextDue(due = day("2026-05-31"), doneOn = day("2026-07-20")))
        assertEquals(day("2027-07-20"), taxes.copy(fromDone = true).nextDue(due = day("2026-05-31"), doneOn = day("2026-07-20")))
    }

    @Test
    fun `a rule by day of the month or of the year takes that of the day it is due on, and keeps counting as it did`() {
        assertEquals(Recurrence.Monthly(15, fromDone = true), Recurrence.Monthly(31, fromDone = true).anchoredTo(day("2026-10-15")))
        assertEquals(Recurrence.Yearly(MonthDay.of(5, 3)), Recurrence.Yearly(MonthDay.of(2, 29)).anchoredTo(day("2027-05-03")))
        assertEquals(Recurrence.EveryNDays(3), Recurrence.EveryNDays(3).anchoredTo(day("2026-10-15")))
    }

    private fun day(text: String) = LocalDate.parse(text)
}
