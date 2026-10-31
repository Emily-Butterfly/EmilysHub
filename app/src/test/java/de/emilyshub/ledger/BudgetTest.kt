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
import java.time.YearMonth
import java.time.ZoneId

/**
 * Budgets of the household book (SPEC.md §13): a monthly one for an expense category, if wanted, and how much of it
 * a month uses; a month closed keeps the one it had then.
 */
@RunWith(AndroidJUnit4::class)
class BudgetTest {

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
    fun `below 80 percent within, from 80 percent near, above 100 percent over`() {
        assertEquals(BudgetLevel.Within, BudgetUse(budget = 10_000, spent = 7_999).level)
        assertEquals(BudgetLevel.Near, BudgetUse(budget = 10_000, spent = 8_000).level)
        assertEquals(BudgetLevel.Near, BudgetUse(budget = 10_000, spent = 10_000).level)
        assertEquals(BudgetLevel.Over, BudgetUse(budget = 10_000, spent = 10_001).level)
    }

    @Test
    fun `what is left, and by how much it is over`() {
        assertEquals(8_750L, BudgetUse(budget = 30_000, spent = 21_250).left)
        assertEquals(-1_250L, BudgetUse(budget = 30_000, spent = 31_250).left)
        assertEquals(1f, BudgetUse(budget = 30_000, spent = 31_250).share)
        assertEquals(0.5f, BudgetUse(budget = 30_000, spent = 15_000).share)
    }

    @Test
    fun `the month shows each budget with what it uses, a budget without expenses too`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        ledger.setBudget(expense("Freizeit"), 5_000)
        book("Lebensmittel", "212,50", "2026-10-12")
        book("Kleidung", "40", "2026-10-13")

        val totals = ledger.month(october).first().expensesByCategory
        assertEquals(
            listOf(Triple("Lebensmittel", 21_250L, 30_000L), Triple("Kleidung", 4_000L, null), Triple("Freizeit", 0L, 5_000L)),
            totals.map { Triple(it.category.name, it.cents, it.budget) },
        )
        assertEquals(8_750L, totals.first().use?.left)
    }

    @Test
    fun `a budget is only for expenses, above zero, and can be taken away again`() = runBlocking {
        val salary = ledger.categories().first().single { it.kind == BookingKind.Income && it.name == "Gehalt" }.id
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.setBudget(salary, 100_000) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.setBudget(expense("Freizeit"), 0) } }

        ledger.setBudget(expense("Freizeit"), 5_000)
        ledger.setBudget(expense("Freizeit"), null)

        assertNull(ledger.categories().first().single { it.name == "Freizeit" }.budgetCents)
    }

    @Test
    fun `a budget changed holds for every open month, a closed one keeps the one it had when it was closed`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        book("Lebensmittel", "100", "2026-08-12")
        book("Lebensmittel", "100", "2026-09-12")
        book("Lebensmittel", "100", "2026-10-12")
        ledger.close(september)

        ledger.setBudget(expense("Lebensmittel"), 40_000)

        assertEquals(40_000L, budgetIn(YearMonth.of(2026, 8)))
        assertEquals(30_000L, budgetIn(september))
        assertEquals(40_000L, budgetIn(october))

        // Opened again, it has the budget of now.
        ledger.reopen(september)
        assertEquals(40_000L, budgetIn(september))
    }

    @Test
    fun `a closed month keeps having no budget where it had none`() = runBlocking {
        book("Lebensmittel", "100", "2026-09-12")
        ledger.close(september)

        ledger.setBudget(expense("Lebensmittel"), 30_000)

        assertNull(budgetIn(september))
    }

    @Test
    fun `saving tells by how much a booking takes its category over its budget`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        book("Lebensmittel", "250", "2026-10-12")

        // Within it still, in another month, or in a category without budget: nothing to tell.
        assertNull(ledger.overrunOf(draft("Lebensmittel", "50", "2026-10-20")))
        assertNull(ledger.overrunOf(draft("Lebensmittel", "100", "2026-09-20")))
        assertNull(ledger.overrunOf(draft("Kleidung", "999", "2026-10-20")))

        assertEquals(1_250L, ledger.overrunOf(draft("Lebensmittel", "62,50", "2026-10-20")))
    }

    @Test
    fun `a booking changed tells only when it raises what goes over`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        val id = book("Lebensmittel", "350", "2026-10-12")
        val saved = checkNotNull(ledger.draftOf(id))

        // Over already, but changed in its description or made smaller: nothing new to tell.
        assertNull(ledger.overrunOf(saved.copy(description = "Wocheneinkauf"), replacing = id))
        assertNull(ledger.overrunOf(saved.copy(amount = "320"), replacing = id))

        assertEquals(10_000L, ledger.overrunOf(saved.copy(amount = "400"), replacing = id))
        // Moved into the month from another one, it counts in full.
        val other = book("Lebensmittel", "100", "2026-09-12")
        assertEquals(15_000L, ledger.overrunOf(checkNotNull(ledger.draftOf(other)).copy(date = LocalDate.parse("2026-10-20")), other))
    }

    private suspend fun budgetIn(month: YearMonth): Long? =
        ledger.month(month).first().expensesByCategory.single { it.category.name == "Lebensmittel" }.budget

    private suspend fun expense(name: String): Long =
        ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == name }.id

    private suspend fun draft(category: String, amount: String, date: String) =
        BookingDraft(BookingKind.Expense, amount, LocalDate.parse(date), expense(category))

    private suspend fun book(category: String, amount: String, date: String): Long = ledger.create(draft(category, amount, date))
}
