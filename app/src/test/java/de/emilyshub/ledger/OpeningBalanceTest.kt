package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

/**
 * The opening balance of a month of the household book (SPEC.md §13, #50): a value of the month, not a booking, set by
 * hand or carried over from the month before; with it the month has an end balance. It never counts as income.
 */
@RunWith(AndroidJUnit4::class)
class OpeningBalanceTest {

    private val database = inMemoryDatabase()

    /** Monday, 2 November 2026, 09:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-02T08:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `an opening balance set by hand gives the month an end balance and is no income`() = runBlocking {
        income("1500", "2026-10-01")
        expense("1450", "2026-10-03")
        assertNull(shown(october).opening)
        assertNull(shown(october).closingBalance)

        ledger.setOpening(october, 20_000)

        val month = shown(october)
        assertEquals(OpeningBalance(october, 20_000, carried = false), month.opening)
        assertEquals(150_000L, month.summary.income)
        assertEquals(5_000L, month.summary.balance)
        assertEquals(25_000L, month.closingBalance)
    }

    @Test
    fun `carried over it is the end balance of the month before, below zero too, and stays so whatever that month does after`() =
        runBlocking {
            ledger.setOpening(september, 10_000)
            expense("300", "2026-09-10")
            assertEquals(PreviousMonth(september, closed = false, endBalance = -20_000), shown(october).previous)

            ledger.carryOver(october)
            assertEquals(OpeningBalance(october, -20_000, carried = true), shown(october).opening)

            expense("50", "2026-09-11")
            assertEquals(-25_000L, shown(october).previous.endBalance)
            assertEquals(-20_000L, shown(october).opening?.cents)
        }

    @Test
    fun `a month without bookings nor opening balance carries over nothing but zero`() = runBlocking {
        ledger.carryOver(november)
        assertEquals(OpeningBalance(november, 0, carried = true), shown(november).opening)
        assertEquals(0L, shown(november).closingBalance)
    }

    @Test
    fun `closing and carrying over puts the end balance into the next month, also across the year, replacing what it had`() =
        runBlocking {
            ledger.setOpening(november, 99)
            ledger.setOpening(october, 1_000)
            income("120", "2026-10-05")
            assertEquals(NextMonth(november, closed = false, OpeningBalance(november, 99, carried = false)), shown(october).next)

            ledger.close(october, carryOver = true)

            assertEquals(true, shown(october).closed)
            assertEquals(OpeningBalance(november, 13_000, carried = true), shown(november).opening)

            val (december, january) = YearMonth.of(2025, 12) to YearMonth.of(2026, 1)
            expense("40", "2025-12-24")
            ledger.close(december, carryOver = true)
            assertEquals(OpeningBalance(january, -4_000, carried = true), shown(january).opening)
        }

    @Test
    fun `closing without carrying over, and opening again, leave every opening balance as it is`() = runBlocking {
        ledger.setOpening(october, 1_000)
        ledger.setOpening(november, 2_000)
        income("120", "2026-10-05")

        ledger.close(october)
        ledger.reopen(october)

        assertEquals(1_000L, shown(october).opening?.cents)
        assertEquals(2_000L, shown(november).opening?.cents)
    }

    @Test
    fun `nothing is carried into a closed month, and then the month before is not closed either`() = runBlocking {
        ledger.setOpening(october, 500)
        ledger.close(october)
        assertEquals(true, shown(september).next.closed)

        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.close(september, carryOver = true) } }

        assertEquals(false, shown(september).closed)
        assertEquals(500L, shown(october).opening?.cents)
    }

    @Test
    fun `the opening balance of a closed month is only to read`() = runBlocking {
        ledger.setOpening(october, 500)
        ledger.close(october)

        listOf(
            suspend { ledger.setOpening(october, 700) },
            suspend { ledger.removeOpening(october) },
            suspend { ledger.carryOver(october) },
        ).forEach { change -> assertThrows(IllegalArgumentException::class.java) { runBlocking { change() } } }
        assertEquals(OpeningBalance(october, 500, carried = false), shown(october).opening)
    }

    @Test
    fun `an opening balance changed by hand is set by hand, and one removed is gone`() = runBlocking {
        ledger.carryOver(november)
        ledger.setOpening(november, -8_520)
        assertEquals(OpeningBalance(november, -8_520, carried = false), shown(november).opening)

        ledger.removeOpening(november)
        assertNull(shown(november).opening)
        assertNull(shown(november).closingBalance)
    }

    @Test
    fun `the archive shows the end balance of a month with an opening balance, its sums as they were`() = runBlocking {
        ledger.setOpening(october, 10_000)
        income("50", "2026-10-02")
        expense("20", "2026-09-02")
        ledger.close(october)
        ledger.close(september)

        assertEquals(
            listOf(MonthSums(october, 5_000, 0, opening = 10_000), MonthSums(september, 0, 2_000)),
            ledger.archive().first(),
        )
        assertEquals(listOf(15_000L, null), ledger.archive().first().map { it.closingBalance })
    }

    @Test
    fun `the year shows the opening balance of January and the end balance of December, if they have one`() = runBlocking {
        val year = Year.of(2026)
        assertNull(ledger.year(year).first().openingOfJanuary)
        assertNull(ledger.year(year).first().closingOfDecember)

        ledger.setOpening(YearMonth.of(2026, 1), 30_000)
        ledger.setOpening(YearMonth.of(2026, 12), -1_000)
        income("100", "2026-12-01")
        expense("10", "2026-01-15")

        val shown = ledger.year(year).first()
        assertEquals(30_000L, shown.openingOfJanuary)
        assertEquals(9_000L, shown.closingOfDecember)
        // Neither counts as income.
        assertEquals(10_000L, shown.income)
        assertEquals(1_000L, shown.expenses)
        assertEquals(10_000L, shown.months.last().income)
    }

    @Test
    fun `an opening balance leaves the budgets as they are`() = runBlocking {
        val food = ledger.categories().first().single { it.name == "Lebensmittel" }.id
        ledger.setBudget(food, 10_000)
        expense("30", "2026-11-01")

        ledger.setOpening(november, -50_000)

        val total = shown(november).summary.expensesByCategory.single { it.category.id == food }
        assertEquals(3_000L, total.cents)
        assertEquals(7_000L, total.use?.left)
    }

    @Test
    fun `this month foresees its end balance with the fixed bookings still to come, others do not`() = runBlocking {
        ledger.setOpening(november, 10_000)
        expense("30", "2026-11-01")
        // Today's is entered already; those to come this month count, others not.
        fixed(BookingKind.Expense, "Wohnen", "850", day = 2)
        fixed(BookingKind.Expense, "Wohnen", "850", day = 15)
        fixed(BookingKind.Income, "Gehalt", "2.500", day = 28)
        fixed(BookingKind.Expense, "Freizeit", "100", day = 20, interval = BookingInterval.Quarterly, first = YearMonth.of(2026, 12))

        val month = shown(november)
        assertEquals(10_000L - 3_000 - 85_000, month.endBalance)
        assertEquals(10_000L - 3_000 - 85_000 - 85_000 + 250_000, month.foreseenEnd)
        assertNull(shown(october).foreseenEnd)
        assertNull(shown(YearMonth.of(2026, 12)).foreseenEnd)
    }

    @Test
    fun `a fixed booking entered and deleted this month is not foreseen again, and with none to come nothing is foreseen`() =
        runBlocking {
            fixed(BookingKind.Expense, "Wohnen", "850", day = 1)
            val entered = shown(november).summary.bookings.single().booking.id
            ledger.delete(entered)

            assertNull(shown(november).foreseenEnd)
        }

    private suspend fun shown(month: YearMonth): LedgerMonth = ledger.month(month).first()

    private suspend fun expense(amount: String, date: String): Long = book(BookingKind.Expense, "Lebensmittel", amount, date)

    private suspend fun income(amount: String, date: String): Long = book(BookingKind.Income, "Gehalt", amount, date)

    private suspend fun book(kind: BookingKind, category: String, amount: String, date: String): Long {
        val id = ledger.categories().first().single { it.kind == kind && it.name == category }.id
        return ledger.create(BookingDraft(kind, amount, LocalDate.parse(date), id))
    }

    private suspend fun fixed(
        kind: BookingKind,
        category: String,
        amount: String,
        day: Int,
        interval: BookingInterval = BookingInterval.Monthly,
        first: YearMonth = november,
    ): Long {
        val id = ledger.categories().first().single { it.kind == kind && it.name == category }.id
        return ledger.createTemplate(RecurringDraft(kind, amount, id, "", interval, day, first))
    }
}
