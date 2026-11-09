package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.todos.RecurrenceEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Fixed bookings of the household book that end (SPEC.md §13, #52): after a number of bookings from the first month on,
 * or on a date, that day included.
 */
@RunWith(AndroidJUnit4::class)
class RecurringEndTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `after a number it ends, those made up for and deleted counted, and is listed last as ended`() = runBlocking {
        val loan = template("Rate", "2026-07", day = 1, RecurrenceEnd.AfterTimes(3))
        val rent = template("Miete", "2026-10", day = 30)
        assertEquals(listOf("2026-07-01 Rate", "2026-08-01 Rate", "2026-09-01 Rate"), entered())

        val august = ledger.month(YearMonth.of(2026, 8)).first().summary.bookings.single().booking.id
        ledger.deleteBooking(august)
        at("2026-12-02T08:00")
        assertEquals(listOf("2026-07-01 Rate", "2026-09-01 Rate", "2026-10-30 Miete", "2026-11-30 Miete"), entered())

        val listed = ledger.templates().first()
        assertEquals(listOf(rent, loan), listed.map { it.id })
        assertNull(listed.last().next)
        assertEquals(RecurrenceEnd.AfterTimes(3), listed.last().draft.end)
    }

    @Test
    fun `on a date it ends with that day, made up for months later too`() = runBlocking {
        template("Abo", "2026-10", day = 15, RecurrenceEnd.OnDate(LocalDate.parse("2026-12-15")))
        at("2027-02-20T08:00")

        assertEquals(listOf("2026-10-15 Abo", "2026-11-15 Abo", "2026-12-15 Abo"), entered())
    }

    @Test
    fun `the end changed later holds from the next one on, which of the series it is told`() = runBlocking {
        val loan = template("Rate", "2026-09", day = 1, RecurrenceEnd.AfterTimes(2))
        assertNull(ledger.templates().first().single().next)

        val editing = checkNotNull(ledger.editingTemplate(loan))
        val saved = ledger.saveTemplate(editing, editing.original!!.copy(end = RecurrenceEnd.AfterTimes(4)))
        assertEquals(Saving.Saved(loan), saved)
        val listed = ledger.templates().first().single()
        assertEquals(LocalDate.parse("2026-11-01"), listed.next)
        // The third of four comes next.
        assertEquals(3, listed.seriesNumber)

        at("2027-03-02T08:00")
        assertEquals(listOf("2026-09-01 Rate", "2026-10-01 Rate", "2026-11-01 Rate", "2026-12-01 Rate"), entered())
    }

    @Test
    fun `a number from 1 to 999 and a date not before the first one`() {
        val draft = RecurringDraft(BookingKind.Expense, "10", 1, "", BookingInterval.Monthly, 15, YearMonth.of(2026, 10))
        assertEquals(emptySet<RecurringProblem>(), draft.copy(end = RecurrenceEnd.AfterTimes(1)).problems)
        assertEquals(setOf(RecurringProblem.EndTimesOutOfRange), draft.copy(end = RecurrenceEnd.AfterTimes(0)).problems)
        assertEquals(setOf(RecurringProblem.EndTimesOutOfRange), draft.copy(end = RecurrenceEnd.AfterTimes(1000)).problems)
        assertEquals(emptySet<RecurringProblem>(), draft.copy(end = RecurrenceEnd.OnDate(LocalDate.parse("2026-10-15"))).problems)
        assertEquals(
            setOf(RecurringProblem.EndBeforeFirst),
            draft.copy(end = RecurrenceEnd.OnDate(LocalDate.parse("2026-10-14"))).problems,
        )
    }

    @Test
    fun `one ended before the month's end is not foreseen`() = runBlocking {
        template("Abo", "2026-09", day = 28, RecurrenceEnd.OnDate(LocalDate.parse("2026-10-27")))
        assertNull(ledger.month(YearMonth.of(2026, 10)).first().foreseenEnd)

        template("Miete", "2026-10", day = 30, RecurrenceEnd.AfterTimes(1))
        assertEquals(-1_000L, ledger.month(YearMonth.of(2026, 10)).first().foreseenEnd)
    }

    private suspend fun template(description: String, first: String, day: Int, end: RecurrenceEnd = RecurrenceEnd.Never): Long {
        val id = ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == "Wohnen" }.id
        val draft = RecurringDraft(BookingKind.Expense, "10", id, description, BookingInterval.Monthly, day, YearMonth.parse(first), end)
        return ledger.createTemplate(draft)
    }

    /** All bookings entered, "2026-10-01 Rate", by date. */
    private suspend fun entered(): List<String> =
        (2026..2027).flatMap { year -> (1..12).flatMap { ledger.month(YearMonth.of(year, it)).first().summary.bookings } }
            .sortedBy { it.booking.date }
            .map { "${it.booking.date} ${it.booking.description}" }

    private fun at(localDateTime: String) {
        clock.now = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()
    }
}
