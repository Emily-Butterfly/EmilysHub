package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Months of the household book closed by hand (SPEC.md §13): then in the archive, only to read, until opened again;
 * no booking is made, changed or deleted in one, nor does a fixed booking come into it.
 */
@RunWith(AndroidJUnit4::class)
class ClosedMonthsTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `months gone by can be closed, the current one from its last day on, later ones not`() = runBlocking {
        assertTrue(ledger.canClose(september))
        assertFalse(ledger.canClose(october))
        assertFalse(ledger.canClose(YearMonth.of(2026, 11)))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.close(october) } }

        at("2026-10-31T00:00")
        assertTrue(ledger.canClose(october))
        ledger.close(october)
        assertTrue(ledger.isClosed(october).first())
    }

    @Test
    fun `a month closed is locked for bookings until it is opened again`() = runBlocking {
        val id = book("Freizeit", "8", "2026-09-12")
        ledger.close(september)

        val draft = checkNotNull(ledger.draftOf(id))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.update(id, draft.copy(amount = "9")) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.delete(id) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { book("Freizeit", "5", "2026-09-30") } }
        // Nor moved out of it, nor into it.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.update(id, draft.copy(date = LocalDate.parse("2026-10-01"))) }
        }
        val october = book("Freizeit", "5", "2026-10-01")
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.update(october, checkNotNull(ledger.draftOf(october)).copy(date = LocalDate.parse("2026-09-01"))) }
        }

        ledger.reopen(september)
        ledger.update(id, draft.copy(amount = "9"))
        assertEquals(900L, ledger.get(id)?.amountCents)
    }

    @Test
    fun `no fixed booking comes into a closed month, nor later once it is opened`() = runBlocking {
        ledger.close(september)
        val rent = ledger.categories().first().single { it.name == "Wohnen" }.id
        val august = YearMonth.of(2026, 8)
        ledger.createTemplate(RecurringDraft(BookingKind.Expense, "850", rent, "Miete", BookingInterval.Monthly, 1, august))

        ledger.reopen(september)
        ledger.enterDue()

        val dates = listOf(august, september, october).flatMap { ledger.month(it).first().bookings }.map { it.booking.date }
        assertEquals(listOf(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-10-01")), dates)
    }

    @Test
    fun `fixed bookings skipped while closed can be entered once the month is open again, each once`() = runBlocking {
        ledger.close(september)
        val rent = ledger.categories().first().single { it.name == "Wohnen" }.id
        val template = ledger.createTemplate(
            RecurringDraft(BookingKind.Expense, "850", rent, "Miete", BookingInterval.Monthly, 1, YearMonth.of(2026, 9)),
        )
        // Not while it is closed.
        assertEquals(0, ledger.skipped(september).first())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.enterSkipped(september) } }

        ledger.reopen(september)
        assertEquals(1, ledger.skipped(september).first())
        ledger.enterSkipped(september)
        ledger.enterSkipped(september)

        val entered = ledger.month(september).first().bookings.single().booking
        assertEquals(LocalDate.parse("2026-09-01") to template, entered.date to entered.recurringId)
        assertEquals(0, ledger.skipped(september).first())
    }

    @Test
    fun `a fixed booking deleted no longer offers what it skipped`() = runBlocking {
        ledger.close(september)
        val rent = ledger.categories().first().single { it.name == "Wohnen" }.id
        val template = ledger.createTemplate(
            RecurringDraft(BookingKind.Expense, "850", rent, "Miete", BookingInterval.Monthly, 1, YearMonth.of(2026, 9)),
        )
        ledger.reopen(september)

        ledger.deleteTemplate(template)

        assertEquals(0, ledger.skipped(september).first())
    }

    @Test
    fun `the archive lists the months closed, the latest first, with their sums`() = runBlocking {
        book("Freizeit", "8", "2026-08-12")
        book("Freizeit", "20", "2026-09-12")
        ledger.close(YearMonth.of(2026, 8))
        ledger.close(september)

        assertEquals(
            listOf(september to -2_000L, YearMonth.of(2026, 8) to -800L),
            ledger.archive().first().map { it.month to it.balance },
        )
    }

    @Test
    fun `earlier months still open with bookings are named, to find them easily`() = runBlocking {
        book("Freizeit", "8", "2026-07-12")
        book("Freizeit", "8", "2026-08-12")
        book("Freizeit", "20", "2026-09-12")
        book("Freizeit", "5", "2026-10-01")
        ledger.close(YearMonth.of(2026, 8))

        assertEquals(listOf(september, YearMonth.of(2026, 7)), ledger.openEarlierMonths().first())
    }

    @Test
    fun `a category with bookings in a closed month is not deleted, as they would move`() = runBlocking {
        val clothes = ledger.categories().first().single { it.name == "Kleidung" }.id
        book("Kleidung", "39,99", "2026-09-12")
        ledger.close(september)

        assertTrue(ledger.inClosedMonths(clothes))
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.deleteCategory(clothes) } }

        ledger.reopen(september)
        assertFalse(ledger.inClosedMonths(clothes))
        ledger.deleteCategory(clothes)
    }

    @Test
    fun `closed on its last day, the month gets that day's fixed booking first`() = runBlocking {
        val rent = ledger.categories().first().single { it.name == "Wohnen" }.id
        ledger.createTemplate(RecurringDraft(BookingKind.Expense, "850", rent, "Miete", BookingInterval.Monthly, 31, october))
        at("2026-10-31T08:00")

        ledger.close(october)

        assertEquals(listOf(LocalDate.parse("2026-10-31")), ledger.month(october).first().bookings.map { it.booking.date })
    }

    private suspend fun book(category: String, amount: String, date: String): Long {
        val id = ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == category }.id
        return ledger.create(BookingDraft(BookingKind.Expense, amount, LocalDate.parse(date), id))
    }

    private fun at(localDateTime: String) {
        clock.now = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()
    }
}
