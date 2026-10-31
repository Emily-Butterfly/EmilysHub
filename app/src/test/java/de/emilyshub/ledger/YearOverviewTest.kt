package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId

/**
 * The year overview of the household book (SPEC.md §13): each month's income, expenses and balance with those of the
 * year, and the year's expenses by category with their shares.
 */
@RunWith(AndroidJUnit4::class)
class YearOverviewTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a year has each of its months with its sums, and those of the year`() = runBlocking {
        book(BookingKind.Income, "Gehalt", "2.500", "2026-01-01")
        book(BookingKind.Expense, "Wohnen", "850", "2026-01-02")
        book(BookingKind.Expense, "Lebensmittel", "45,30", "2026-01-31")
        book(BookingKind.Expense, "Freizeit", "20", "2026-03-15")
        book(BookingKind.Income, "Sonstiges", "100", "2026-03-20")

        val year = ledger.year(Year.of(2026)).first()

        assertEquals((1..12).map { YearMonth.of(2026, it) }, year.months.map { it.month })
        val (january, february, march) = year.months
        assertEquals(Triple(250_000L, 89_530L, 160_470L), Triple(january.income, january.expenses, january.balance))
        assertEquals(Triple(0L, 0L, 0L), Triple(february.income, february.expenses, february.balance))
        assertEquals(Triple(10_000L, 2_000L, 8_000L), Triple(march.income, march.expenses, march.balance))
        assertEquals(Triple(260_000L, 91_530L, 168_470L), Triple(year.income, year.expenses, year.balance))
    }

    @Test
    fun `the year's expenses by category have their sum and share, the largest first`() = runBlocking {
        book(BookingKind.Expense, "Wohnen", "850", "2026-01-02")
        book(BookingKind.Expense, "Wohnen", "850", "2026-02-02")
        book(BookingKind.Expense, "Lebensmittel", "200", "2026-01-10")
        book(BookingKind.Expense, "Lebensmittel", "100", "2026-12-10")
        book(BookingKind.Expense, "Freizeit", "100", "2026-06-10")
        // Income is not among them.
        book(BookingKind.Income, "Gehalt", "2.500", "2026-01-01")

        val year = ledger.year(Year.of(2026)).first()

        assertEquals(
            listOf("Wohnen" to 170_000L, "Lebensmittel" to 30_000L, "Freizeit" to 10_000L),
            year.expensesByCategory.map { it.category.name to it.cents },
        )
        assertEquals(listOf(81, 14, 5), year.expensesByCategory.map { year.shareOf(it) })
    }

    @Test
    fun `a year ends with New Year's Eve and the next begins on New Year's Day`() = runBlocking {
        book(BookingKind.Expense, "Freizeit", "50", "2025-12-31", "Silvester")
        book(BookingKind.Expense, "Freizeit", "30", "2026-01-01", "Neujahr")
        book(BookingKind.Expense, "Freizeit", "70", "2026-12-31", "Silvester")
        book(BookingKind.Expense, "Freizeit", "90", "2027-01-01", "Neujahr")

        val last = ledger.year(Year.of(2025)).first()
        val current = ledger.year(Year.of(2026)).first()
        val next = ledger.year(Year.of(2027)).first()

        assertEquals(5_000L, last.expenses)
        assertEquals(5_000L, last.months.last().expenses)
        assertEquals(10_000L, current.expenses)
        assertEquals(listOf(3_000L, 7_000L), listOf(current.months.first().expenses, current.months.last().expenses))
        assertEquals(9_000L, next.expenses)
        assertEquals(9_000L, next.months.first().expenses)
    }

    @Test
    fun `months open and closed count the same`() = runBlocking {
        book(BookingKind.Expense, "Freizeit", "20", "2026-08-12")
        book(BookingKind.Expense, "Freizeit", "30", "2026-09-12")
        ledger.close(YearMonth.of(2026, 8))

        val year = ledger.year(Year.of(2026)).first()

        assertEquals(listOf(2_000L, 3_000L), year.months.subList(7, 9).map { it.expenses })
        assertEquals(5_000L, year.expenses)
    }

    @Test
    fun `fixed bookings due by today are entered first`() = runBlocking {
        val rent = category(BookingKind.Expense, "Wohnen")
        ledger.createTemplate(
            RecurringDraft(BookingKind.Expense, "850", rent, "Miete", BookingInterval.Monthly, day = 1, firstMonth = YearMonth.of(2026, 10)),
        )

        assertEquals(85_000L, ledger.year(Year.of(2026)).first().expenses)
    }

    @Test
    fun `a year without bookings is all zero and has no expenses by category`() = runBlocking {
        val year = ledger.year(Year.of(2024)).first()

        assertEquals(12, year.months.size)
        assertEquals(0L, year.balance)
        assertEquals(emptyList<CategoryTotal>(), year.expensesByCategory)
    }

    private suspend fun category(kind: BookingKind, name: String): Long =
        ledger.categories().first().single { it.kind == kind && it.name == name }.id

    private suspend fun book(kind: BookingKind, category: String, amount: String, date: String, description: String = ""): Long =
        ledger.create(BookingDraft(kind, amount, LocalDate.parse(date), category(kind, category), description))
}
