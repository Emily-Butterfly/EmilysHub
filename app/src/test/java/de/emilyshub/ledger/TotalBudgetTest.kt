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
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The total budget of the household book (SPEC.md §13): none, a fixed amount or one from the month's income less a
 * saving, as the standard for every month open, each of which can have its own; a month closed keeps its amount.
 */
@RunWith(AndroidJUnit4::class)
class TotalBudgetTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val december = YearMonth.of(2026, 12)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the standard is none at first, then a fixed amount or one from income, for every month open`() = runBlocking {
        assertEquals(TotalBudget.None, ledger.totalBudget().first())
        assertNull(totalIn(october))

        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        assertEquals(TotalBudget.Fixed(200_000), ledger.totalBudget().first())
        assertEquals(200_000L, totalIn(october))
        assertEquals(200_000L, totalIn(december))

        book("Gehalt", BookingKind.Income, "2.500", "2026-10-01")
        ledger.setTotalBudget(TotalBudget.FromIncome(savingCents = 50_000))
        assertEquals(200_000L, totalIn(october))
        assertEquals(0L, totalIn(december))

        ledger.setTotalBudget(TotalBudget.None)
        assertNull(totalIn(october))
    }

    @Test
    fun `a fixed total budget is above zero, a saving not below it`() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.setTotalBudget(TotalBudget.Fixed(0)) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.setTotalBudget(TotalBudget.FromIncome(-1)) } }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.setTotalBudgetIn(october, TotalBudget.Fixed(-5)) }
        }
        ledger.setTotalBudget(TotalBudget.FromIncome(0))
        assertEquals(TotalBudget.FromIncome(0), ledger.totalBudget().first())
    }

    @Test
    fun `from income counts the income entered and the fixed income still to come, never the opening balance`() = runBlocking {
        val salary = category(BookingKind.Income, "Gehalt")
        ledger.createTemplate(
            RecurringDraft(BookingKind.Income, "2.500", salary, "Gehalt", BookingInterval.Monthly, day = 28, firstMonth = october),
        )
        // An expense to come counts in no income.
        ledger.createTemplate(
            RecurringDraft(
                BookingKind.Expense, "850", category(BookingKind.Expense, "Wohnen"), "Miete", BookingInterval.Monthly, day = 30,
                firstMonth = october,
            ),
        )
        book("Sonstiges", BookingKind.Income, "120", "2026-10-05")
        ledger.setOpening(october, 100_000)

        ledger.setTotalBudget(TotalBudget.FromIncome(savingCents = 20_000))

        assertEquals(262_000L - 20_000L, totalIn(october))
        assertEquals(250_000L - 20_000L, totalIn(december))
    }

    @Test
    fun `from income is never below zero`() = runBlocking {
        book("Gehalt", BookingKind.Income, "100", "2026-10-01")
        ledger.setTotalBudget(TotalBudget.FromIncome(savingCents = 50_000))

        assertEquals(0L, totalIn(october))
    }

    @Test
    fun `an open month can have its own, which the standard changed leaves, and as usual takes it back`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        book("Gehalt", BookingKind.Income, "2.500", "2026-12-01")

        ledger.setTotalBudgetIn(december, TotalBudget.FromIncome(savingCents = 30_000))
        ledger.setTotalBudgetIn(october, TotalBudget.None)
        ledger.setTotalBudget(TotalBudget.Fixed(180_000))

        assertEquals(220_000L, totalIn(december))
        assertNull(totalIn(october))
        assertEquals(180_000L, totalIn(september))
        assertTrue(ledger.month(december).first().ownTotal)
        assertFalse(ledger.month(september).first().ownTotal)

        ledger.setTotalBudgetIn(december, null)
        assertEquals(180_000L, totalIn(december))
        assertFalse(ledger.month(december).first().ownTotal)
    }

    @Test
    fun `set to the standard, a month has none of its own and follows it`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        ledger.setTotalBudgetIn(october, TotalBudget.Fixed(200_000))
        ledger.setTotalBudget(TotalBudget.Fixed(150_000))

        assertEquals(150_000L, totalIn(october))
        assertEquals(TotalBudget.Fixed(150_000), ledger.month(october).first().totalSetting)
    }

    @Test
    fun `set to the standard, a month follows it only while open, closed it keeps the amount of then`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        ledger.setTotalBudgetIn(september, TotalBudget.Fixed(200_000))
        ledger.close(september)

        ledger.setTotalBudget(TotalBudget.Fixed(150_000))

        assertEquals(200_000L, totalIn(september))
        assertEquals(150_000L, totalIn(october))
    }

    @Test
    fun `a month closed keeps its total budget as an amount, and has its setting again once opened`() = runBlocking {
        book("Gehalt", BookingKind.Income, "2.000", "2026-09-01")
        ledger.setTotalBudget(TotalBudget.Fixed(150_000))
        ledger.setTotalBudgetIn(september, TotalBudget.FromIncome(savingCents = 10_000))
        ledger.close(september)

        ledger.setTotalBudget(TotalBudget.Fixed(100_000))
        book("Gehalt", BookingKind.Income, "500", "2026-10-01")
        assertEquals(190_000L, totalIn(september))
        assertNull(ledger.month(september).first().totalSetting)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ledger.setTotalBudgetIn(september, TotalBudget.None) }
        }

        ledger.reopen(september)
        assertEquals(TotalBudget.FromIncome(10_000), ledger.month(september).first().totalSetting)
        assertEquals(190_000L, totalIn(september))
    }

    @Test
    fun `a month closed without a total budget keeps having none`() = runBlocking {
        ledger.close(september)
        ledger.setTotalBudget(TotalBudget.Fixed(150_000))

        assertNull(totalIn(september))
    }

    @Test
    fun `the month shows what is spent of its total budget`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(100_000))
        book("Lebensmittel", BookingKind.Expense, "600", "2026-10-02")
        book("Freizeit", BookingKind.Expense, "250", "2026-10-03")

        val use = checkNotNull(ledger.month(october).first().totalUse)
        assertEquals(BudgetUse(budget = 100_000, spent = 85_000), use)
        assertEquals(BudgetLevel.Near, use.level)
    }

    @Test
    fun `a total budget of nothing is within it while nothing is spent, and over it once anything is`() {
        assertEquals(BudgetLevel.Within, BudgetUse(budget = 0, spent = 0).level)
        assertEquals(0f, BudgetUse(budget = 0, spent = 0).share)
        assertEquals(BudgetLevel.Over, BudgetUse(budget = 0, spent = 1).level)
        assertEquals(1f, BudgetUse(budget = 0, spent = 1).share)
    }

    @Test
    fun `saving tells by how much a booking takes the month over its total budget`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(100_000))
        ledger.setTotalBudgetIn(september, TotalBudget.None)
        book("Lebensmittel", BookingKind.Expense, "900", "2026-10-02")

        assertNull(ledger.totalOverrunOf(draft("Freizeit", BookingKind.Expense, "100", "2026-10-20")))
        assertEquals(5_000L, ledger.totalOverrunOf(draft("Freizeit", BookingKind.Expense, "150", "2026-10-20")))
        // Income, or a month without one: nothing to tell.
        assertNull(ledger.totalOverrunOf(draft("Gehalt", BookingKind.Income, "5.000", "2026-10-20")))
        assertNull(ledger.totalOverrunOf(draft("Freizeit", BookingKind.Expense, "5.000", "2026-09-20")))
    }

    @Test
    fun `a booking changed tells of the total budget only when it raises what goes over`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(100_000))
        val id = book("Lebensmittel", BookingKind.Expense, "1.200", "2026-10-02")
        val saved = ledger.savedDraft(id)

        assertNull(ledger.totalOverrunOf(saved.copy(description = "Großeinkauf"), replacing = id))
        assertNull(ledger.totalOverrunOf(saved.copy(amount = "1.100"), replacing = id))
        // Into another category of the month, it counts the same.
        val moved = saved.copy(amount = "1.300", categoryId = category(BookingKind.Expense, "Freizeit"))
        assertEquals(30_000L, ledger.totalOverrunOf(moved, replacing = id))
    }

    private suspend fun totalIn(month: YearMonth): Long? = ledger.month(month).first().totalBudget

    private suspend fun category(kind: BookingKind, name: String): Long =
        ledger.categories().first().single { it.kind == kind && it.name == name }.id

    private suspend fun draft(category: String, kind: BookingKind, amount: String, date: String) =
        BookingDraft(kind, amount, LocalDate.parse(date), category(kind, category))

    private suspend fun book(category: String, kind: BookingKind, amount: String, date: String): Long =
        ledger.saveNew(draft(category, kind, amount, date))
}
