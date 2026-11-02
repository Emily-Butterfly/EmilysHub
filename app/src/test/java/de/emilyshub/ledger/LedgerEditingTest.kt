package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * Editing bookings and fixed bookings of the household book (SPEC.md §13): what the editors open with, what keeps a
 * draft from being saved, a month closed or a first month too far back, and what saving and deleting do then; without
 * the editors.
 */
@RunWith(AndroidJUnit4::class)
class LedgerEditingTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)
    private val september = YearMonth.of(2026, 9)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a booking opens to edit as it was saved, and one deleted meanwhile does not`() = runBlocking {
        val id = saved(booking("8", "2026-09-12"))

        val editing = checkNotNull(ledger.editing(id))
        assertEquals(id, editing.id)
        assertEquals("8", editing.original?.amount)
        assertEquals(september, editing.savedMonth)

        ledger.delete(editing)
        assertNull(ledger.editing(id))
    }

    @Test
    fun `a new booking is dated today`() {
        assertEquals(LocalDate.parse("2026-10-23"), ledger.newDraft(BookingKind.Expense).date)
    }

    @Test
    fun `a booking in a month closed, or moved out of one or into one, is locked by it`() = runBlocking {
        val id = saved(booking("8", "2026-09-12"))
        ledger.close(september)
        val closed = ledger.closedMonths().first()
        val editing = checkNotNull(ledger.editing(id))
        val draft = checkNotNull(editing.original)

        assertEquals(september, editing.closedMonth(draft, closed))
        assertEquals(september, editing.closedMonth(draft.copy(date = LocalDate.parse("2026-10-01")), closed))
        assertEquals(september, BookingEditing.New.closedMonth(booking("5", "2026-09-30"), closed))
        assertNull(BookingEditing.New.closedMonth(booking("5", "2026-10-01"), closed))
        assertTrue(BookingProblem.MonthClosed in ledger.problems(editing, draft, closed))
    }

    @Test
    fun `saving in a month closed meanwhile is refused, and nothing changes`() = runBlocking {
        val id = saved(booking("8", "2026-09-12"))
        val editing = checkNotNull(ledger.editing(id))
        ledger.close(september)

        val saving = ledger.save(editing, checkNotNull(editing.original).copy(amount = "9"))

        assertEquals(Saving.Refused(setOf(BookingProblem.MonthClosed)), saving)
        assertEquals(800L, ledger.get(id)?.amountCents)
        assertEquals(
            Saving.Refused(setOf(BookingProblem.MonthClosed)),
            ledger.save(BookingEditing.New, booking("5", "2026-09-30")),
        )
    }

    @Test
    fun `deleting in a month closed meanwhile keeps the booking`() = runBlocking {
        val id = saved(booking("8", "2026-09-12"))
        val editing = checkNotNull(ledger.editing(id))
        ledger.close(september)

        assertFalse(ledger.delete(editing))

        assertEquals(800L, ledger.get(id)?.amountCents)
    }

    @Test
    fun `a booking with problems is refused`() = runBlocking {
        assertEquals(
            Saving.Refused(setOf(BookingProblem.AmountNotAboveZero)),
            ledger.save(BookingEditing.New, booking("0", "2026-10-01")),
        )
    }

    @Test
    fun `saving a booking opened saves its changes`() = runBlocking {
        val id = saved(booking("8", "2026-10-12"))
        val editing = checkNotNull(ledger.editing(id))

        assertEquals(Saving.Saved(id), ledger.save(editing, checkNotNull(editing.original).copy(amount = "9,50")))

        assertEquals(950L, ledger.get(id)?.amountCents)
    }

    @Test
    fun `a new fixed booking starts this month, on today's day`() {
        val draft = ledger.newTemplate()

        assertEquals(YearMonth.of(2026, 10), draft.firstMonth)
        assertEquals(23, draft.day)
    }

    @Test
    fun `a fixed booking's first month is a year back at most, as all months since are entered at once`() = runBlocking {
        val new = RecurringEditing.New
        assertEquals(YearMonth.of(2025, 10), ledger.earliestFirstMonth(new))
        assertEquals(emptySet<RecurringProblem>(), ledger.problems(new, template(first = "2025-10")))
        assertEquals(setOf(RecurringProblem.FirstMonthTooEarly), ledger.problems(new, template(first = "2025-09")))
        assertEquals(Saving.Refused(setOf(RecurringProblem.FirstMonthTooEarly)), ledger.saveTemplate(new, template(first = "2025-09")))
        assertTrue(ledger.templates().first().isEmpty())
    }

    @Test
    fun `a fixed booking saved with an earlier first month keeps it`() = runBlocking {
        // Saved a year and a half ago, while that was not yet too far back.
        clock.now = Instant.parse("2025-04-23T05:00:00Z")
        val id = (ledger.saveTemplate(RecurringEditing.New, template(first = "2025-04")) as Saving.Saved).id
        clock.now = Instant.parse("2026-10-23T05:00:00Z")

        val editing = checkNotNull(ledger.editingTemplate(id))
        assertEquals(YearMonth.of(2025, 4), ledger.earliestFirstMonth(editing))
        val renamed = checkNotNull(editing.original).copy(description = "Miete")

        assertEquals(Saving.Saved(id), ledger.saveTemplate(editing, renamed))
        assertEquals(setOf(RecurringProblem.FirstMonthTooEarly), ledger.problems(editing, renamed.copy(firstMonth = YearMonth.of(2025, 3))))
    }

    @Test
    fun `a fixed booking deleted meanwhile does not open`() = runBlocking {
        val id = (ledger.saveTemplate(RecurringEditing.New, template(first = "2026-10")) as Saving.Saved).id
        ledger.deleteTemplate(id)

        assertNull(ledger.editingTemplate(id))
    }

    private suspend fun saved(draft: BookingDraft): Long = (ledger.save(BookingEditing.New, draft) as Saving.Saved).id

    private suspend fun leisure(): Long =
        ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == "Freizeit" }.id

    private suspend fun booking(amount: String, date: String) =
        BookingDraft(BookingKind.Expense, amount, LocalDate.parse(date), leisure())

    private suspend fun template(first: String) =
        RecurringDraft(BookingKind.Expense, "850", leisure(), "Miete", BookingInterval.Monthly, 1, YearMonth.parse(first))
}
