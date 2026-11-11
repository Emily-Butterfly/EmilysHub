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
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

/**
 * The budget calculator of the household book (SPEC.md §13): every expense category with its budget and the average of
 * its expenses, their sum against the total budget, all saved at once, from the categories or from a month open; and the
 * total budgets in the archive and the year overview.
 */
@RunWith(AndroidJUnit4::class)
class BudgetPlanTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)
    private val october = YearMonth.of(2026, 10)
    private val december = YearMonth.of(2026, 12)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `from the categories it has every expense category with its monthly budget, against the standard`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        ledger.setBudgetIn(october, expense("Lebensmittel"), 45_000)
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))

        val plan = ledger.budgetPlan(month = null)

        assertNull(plan.month)
        assertEquals(
            listOf("Freizeit", "Gesundheit", "Kleidung", "Lebensmittel", "Mobilität", "Wohnen", "Sonstiges"),
            plan.lines.map { it.category.name },
        )
        assertEquals(30_000L, plan.lines.single { it.category.name == "Lebensmittel" }.budget)
        assertEquals(TotalBudget.Fixed(200_000), plan.total)
        assertEquals(october, plan.incomeMonth)
    }

    @Test
    fun `from income, the standard reckons with the income of this month`() = runBlocking {
        book("Gehalt", BookingKind.Income, "2.500", "2026-10-01")
        ledger.setTotalBudget(TotalBudget.FromIncome(50_000))

        val plan = ledger.budgetPlan(month = null)

        assertEquals(250_000L, plan.income)
        assertEquals(200_000L, PlanCheck(plan, PlanTyped.of(plan)).totalAmount)
    }

    @Test
    fun `from a month it has the budgets of that month against its total budget, its own or the standard`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        ledger.setBudgetIn(december, expense("Lebensmittel"), 45_000)
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))

        val plan = ledger.budgetPlan(december)
        assertEquals(45_000L, plan.lines.single { it.category.name == "Lebensmittel" }.budget)
        assertNull(plan.total)
        assertEquals(TotalBudget.Fixed(200_000), PlanCheck(plan, PlanTyped.of(plan)).effective)

        ledger.setTotalBudgetIn(december, TotalBudget.Fixed(250_000))
        assertEquals(TotalBudget.Fixed(250_000), ledger.budgetPlan(december).total)
    }

    @Test
    fun `a closed month offers no calculator`() = runBlocking {
        ledger.close(YearMonth.of(2026, 9))

        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.budgetPlan(YearMonth.of(2026, 9)) } }
        Unit
    }

    @Test
    fun `next to each budget the average of the last three closed months, fewer if there are fewer`() = runBlocking {
        assertTrue(ledger.budgetPlan(month = null).lines.all { it.average == null })

        book("Lebensmittel", BookingKind.Expense, "1.000", "2026-05-10")
        book("Lebensmittel", BookingKind.Expense, "300", "2026-07-10")
        book("Lebensmittel", BookingKind.Expense, "200", "2026-08-10")
        book("Lebensmittel", BookingKind.Expense, "100", "2026-08-11")
        book("Freizeit", BookingKind.Expense, "50", "2026-09-10")
        // Open months do not count.
        book("Lebensmittel", BookingKind.Expense, "999", "2026-10-10")

        ledger.close(YearMonth.of(2026, 9))
        var lines = ledger.budgetPlan(month = null).lines
        assertEquals(0L, lines.single { it.category.name == "Lebensmittel" }.average)
        assertEquals(5_000L, lines.single { it.category.name == "Freizeit" }.average)

        ledger.close(YearMonth.of(2026, 5))
        ledger.close(YearMonth.of(2026, 7))
        ledger.close(YearMonth.of(2026, 8))
        lines = ledger.budgetPlan(month = null).lines
        // July, August and September: 300 + 300 + 0, by three.
        assertEquals(20_000L, lines.single { it.category.name == "Lebensmittel" }.average)
        assertEquals(1_667L, lines.single { it.category.name == "Freizeit" }.average)
    }

    @Test
    fun `the sum counts those without a budget as nothing, and what is left of the total budget or over it`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(100_000))
        val plan = ledger.budgetPlan(month = null)
        val typed = PlanTyped.of(plan).withBudget(expense("Lebensmittel"), "400").withBudget(expense("Wohnen"), "550")

        val check = PlanCheck(plan, typed)
        assertEquals(95_000L, check.sum)
        assertEquals(5_000L, check.left)
        assertTrue(check.canSave)

        val over = PlanCheck(plan, typed.withBudget(expense("Freizeit"), "170"))
        assertEquals(-12_000L, over.left)
        assertTrue(over.canSave)

        assertNull(PlanCheck(plan, typed.withTotal(TotalChoice.None)).left)
    }

    @Test
    fun `a field with a problem keeps it from being saved`() = runBlocking {
        val plan = ledger.budgetPlan(month = null)
        val typed = PlanTyped.of(plan)

        val zero = PlanCheck(plan, typed.withBudget(expense("Lebensmittel"), "0"))
        assertEquals(mapOf(expense("Lebensmittel") to BudgetProblem.NotAboveZero), zero.problems)
        assertFalse(zero.canSave)

        assertFalse(PlanCheck(plan, typed.withTotal(TotalChoice.Fixed).copy(amount = "")).canSave)
        val savingTyped = typed.withTotal(TotalChoice.FromIncome).copy(saving = "x")
        assertEquals(BudgetProblem.AmountInvalid, PlanCheck(plan, savingTyped).savingProblem)
        // No saving typed is none.
        assertEquals(TotalBudget.FromIncome(0), PlanCheck(plan, typed.withTotal(TotalChoice.FromIncome).copy(saving = "")).total)
    }

    @Test
    fun `saved from the categories, it sets the monthly budgets and the standard all at once, over it too`() = runBlocking {
        ledger.setBudget(expense("Freizeit"), 5_000)
        val plan = ledger.budgetPlan(month = null)
        val typed = PlanTyped.of(plan).withBudget(expense("Lebensmittel"), "900").withBudget(expense("Freizeit"), "")
            .withTotal(TotalChoice.Fixed).copy(amount = "800")

        ledger.saveBudgetPlan(PlanCheck(plan, typed))

        val categories = ledger.categories().first()
        assertEquals(90_000L, categories.single { it.name == "Lebensmittel" }.budgetCents)
        assertNull(categories.single { it.name == "Freizeit" }.budgetCents)
        assertEquals(TotalBudget.Fixed(80_000), ledger.totalBudget().first())
    }

    @Test
    fun `saved from a month, what differs from the monthly budget is the month's own, what equals it is none`() = runBlocking {
        ledger.setBudget(expense("Lebensmittel"), 30_000)
        ledger.setBudget(expense("Freizeit"), 5_000)
        ledger.setBudgetIn(december, expense("Freizeit"), 8_000)
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        val plan = ledger.budgetPlan(december)
        val typed = PlanTyped.of(plan).withBudget(expense("Lebensmittel"), "450").withBudget(expense("Freizeit"), "50")
            .withTotal(TotalChoice.FromIncome).copy(saving = "100")

        ledger.saveBudgetPlan(PlanCheck(plan, typed))

        val month = ledger.month(december).first()
        assertEquals(
            listOf(Triple("Lebensmittel", 45_000L, 30_000L)),
            month.budgets.filter { it.ownInMonth }.map { Triple(it.category.name, it.inMonth, it.monthly) },
        )
        assertEquals(TotalBudget.FromIncome(10_000), month.totalSetting)
        assertTrue(month.ownTotal)
        // The monthly ones and the standard stay.
        assertEquals(30_000L, ledger.categories().first().single { it.name == "Lebensmittel" }.budgetCents)
        assertEquals(TotalBudget.Fixed(200_000), ledger.totalBudget().first())

        val again = ledger.budgetPlan(december)
        ledger.saveBudgetPlan(PlanCheck(again, PlanTyped.of(again).withTotal(TotalChoice.AsUsual)))
        assertFalse(ledger.month(december).first().ownTotal)
    }

    @Test
    fun `the archive has each month's total budget as kept`() = runBlocking {
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        book("Lebensmittel", BookingKind.Expense, "1.840", "2026-09-10")
        ledger.close(YearMonth.of(2026, 9))
        ledger.setTotalBudget(TotalBudget.None)
        ledger.close(YearMonth.of(2026, 8))

        val archive = ledger.archive().first()
        assertEquals(listOf(200_000L, null), archive.map { it.totalBudget })
        assertEquals(16_000L, archive.first().totalUse?.left)
    }

    @Test
    fun `the year has each month's total budget, and their sum next to the expenses of the same months`() = runBlocking {
        book("Lebensmittel", BookingKind.Expense, "1.000", "2026-08-10")
        book("Lebensmittel", BookingKind.Expense, "2.100", "2026-09-10")
        book("Lebensmittel", BookingKind.Expense, "500", "2026-10-10")
        ledger.setTotalBudget(TotalBudget.Fixed(200_000))
        ledger.close(YearMonth.of(2026, 9))
        ledger.setTotalBudget(TotalBudget.None)
        ledger.setTotalBudgetIn(october, TotalBudget.Fixed(150_000))

        val year = ledger.year(Year.of(2026)).first()
        assertEquals(
            listOf(null, 200_000L, 150_000L, null),
            year.months.subList(7, 11).map { it.totalBudget },
        )
        assertEquals(350_000L, year.totalBudget)
        // August had none: its expenses count on neither side.
        assertEquals(260_000L, year.expensesOfBudgeted)
        assertEquals(360_000L, year.expenses)
    }

    @Test
    fun `a year without any total budget has none`() = runBlocking {
        assertNull(ledger.year(Year.of(2026)).first().totalBudget)
    }

    private suspend fun expense(name: String): Long =
        ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == name }.id

    private suspend fun category(kind: BookingKind, name: String): Long =
        ledger.categories().first().single { it.kind == kind && it.name == name }.id

    private suspend fun book(category: String, kind: BookingKind, amount: String, date: String): Long =
        ledger.saveNew(BookingDraft(kind, amount, LocalDate.parse(date), category(kind, category)))
}
