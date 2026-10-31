package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitAll
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
import java.util.concurrent.Executors

/**
 * The household book (SPEC.md §13): bookings of income and expenses in categories of their own, and each month's
 * sums, balance and expenses by category.
 */
@RunWith(AndroidJUnit4::class)
class LedgerTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)
    private val october = YearMonth.of(2026, 10)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `it starts with suggested categories of each kind, "Sonstiges" last`() = runBlocking {
        val categories = ledger.categories().first()

        assertEquals(
            listOf("Freizeit", "Gesundheit", "Kleidung", "Lebensmittel", "Mobilität", "Wohnen", "Sonstiges"),
            categories.filter { it.kind == BookingKind.Expense }.map { it.name },
        )
        assertEquals(listOf("Gehalt", "Sonstiges"), categories.filter { it.kind == BookingKind.Income }.map { it.name })
        // Only once.
        assertEquals(categories, ledger.categories().first())
    }

    @Test
    fun `a month sums its income and expenses, its balance, and its expenses by category, the largest first`() = runBlocking {
        book(BookingKind.Income, "Gehalt", "2.500,00", "2026-10-01")
        book(BookingKind.Expense, "Lebensmittel", "45,30", "2026-10-03", "Wocheneinkauf")
        book(BookingKind.Expense, "Lebensmittel", "12,20", "2026-10-10")
        book(BookingKind.Expense, "Wohnen", "850,00", "2026-10-02", "Miete")
        // Not of October.
        book(BookingKind.Expense, "Wohnen", "850,00", "2026-09-30")
        book(BookingKind.Expense, "Wohnen", "850,00", "2026-11-01")

        val month = ledger.month(october).first()

        assertEquals(250_000L, month.income)
        assertEquals(90_750L, month.expenses)
        assertEquals(159_250L, month.balance)
        assertEquals(listOf("Wohnen" to 85_000L, "Lebensmittel" to 5_750L), month.expensesByCategory.map { it.category.name to it.cents })
        // The latest first.
        assertEquals(listOf("2026-10-10", "2026-10-03", "2026-10-02", "2026-10-01"), month.bookings.map { it.booking.date.toString() })
        assertEquals("Wocheneinkauf", month.bookings[1].booking.description)
        assertEquals("Lebensmittel", month.bookings[1].category.name)
    }

    @Test
    fun `a month ends with its last day, in a leap year too`() = runBlocking {
        book(BookingKind.Expense, "Freizeit", "10", "2028-02-29")
        book(BookingKind.Expense, "Freizeit", "20", "2028-03-01")

        assertEquals(1_000L, ledger.month(YearMonth.of(2028, 2)).first().expenses)
        assertEquals(2_000L, ledger.month(YearMonth.of(2028, 3)).first().expenses)
        assertEquals(0L, ledger.month(YearMonth.of(2028, 4)).first().balance)
    }

    @Test
    fun `a booking is changed and deleted`() = runBlocking {
        val id = book(BookingKind.Expense, "Freizeit", "10", "2026-10-05", "Kino")

        val draft = checkNotNull(ledger.draftOf(id))
        ledger.update(id, draft.copy(amount = "12,50", description = "Kino mit Popcorn"))
        assertEquals(1_250L to "Kino mit Popcorn", ledger.get(id)?.let { it.amountCents to it.description })

        ledger.delete(id)
        assertNull(ledger.get(id))
    }

    @Test
    fun `an amount above zero, a category of its kind and a short description are needed`() = runBlocking {
        val food = category(BookingKind.Expense, "Lebensmittel")
        val salary = category(BookingKind.Income, "Gehalt")
        val draft = BookingDraft(BookingKind.Expense, amount = "4,50", date = LocalDate.parse("2026-10-23"), categoryId = food)

        assertEquals(emptySet<BookingProblem>(), draft.problems)
        assertEquals(setOf(BookingProblem.AmountMissing), draft.copy(amount = " ").problems)
        assertEquals(setOf(BookingProblem.AmountInvalid), draft.copy(amount = "4.5").problems)
        assertEquals(setOf(BookingProblem.AmountNotAboveZero), draft.copy(amount = "0,00").problems)
        assertEquals(setOf(BookingProblem.CategoryMissing), draft.copy(categoryId = null).problems)
        assertEquals(setOf(BookingProblem.DescriptionTooLong), draft.copy(description = "x".repeat(201)).problems)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.create(draft.copy(amount = "0")) } }
        // Income in a category of expenses is refused.
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.create(draft.copy(categoryId = salary)) } }
        Unit
    }

    @Test
    fun `the date is today unless chosen, and the description is cleaned`() = runBlocking {
        val draft = ledger.newDraft(BookingKind.Expense)
        assertEquals(LocalDate.parse("2026-10-23"), draft.date)

        val id = ledger.create(draft.copy(amount = "3", categoryId = category(BookingKind.Expense, "Freizeit"), description = "  Eis\n "))
        assertEquals("Eis", ledger.get(id)?.description)
    }

    @Test
    fun `categories are created and renamed, unique within their kind`() = runBlocking {
        val travel = ledger.createCategory(BookingKind.Expense, " Reisen ")
        ledger.createCategory(BookingKind.Income, "Reisen")

        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.createCategory(BookingKind.Expense, "reisen") } }
        ledger.renameCategory(travel, "Urlaub")
        assertEquals("Urlaub", ledger.categories().first().single { it.id == travel }.name)
    }

    @Test
    fun `a category deleted moves its bookings to "Sonstiges" of its kind, and the two "Sonstiges" stay`() = runBlocking {
        val clothes = category(BookingKind.Expense, "Kleidung")
        val id = book(BookingKind.Expense, "Kleidung", "39,99", "2026-10-12")
        assertEquals(1, ledger.bookingsIn(clothes))

        ledger.deleteCategory(clothes)

        val other = ledger.categories().first().single { it.kind == BookingKind.Expense && it.isOther }
        assertEquals(other.id, ledger.get(id)?.categoryId)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.deleteCategory(other.id) } }
        assertEquals(listOf("Sonstiges" to 3_999L), ledger.month(october).first().expensesByCategory.map { it.category.name to it.cents })
    }

    @Test
    fun `looked at twice at once, the suggestions still come once`() = runBlocking {
        val io = Executors.newFixedThreadPool(2).asCoroutineDispatcher()
        val parallel = Ledger({ database }, clock, io)
        try {
            listOf(async(io) { parallel.categories().first() }, async(io) { parallel.categories().first() }).awaitAll()
        } finally {
            io.close()
        }

        assertEquals(9, ledger.categories().first().size)
        assertEquals(2, ledger.categories().first().count { it.isOther })
    }

    @Test
    fun `"Sonstiges" keeps its name`() = runBlocking {
        val other = ledger.categories().first().first { it.isOther }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.renameCategory(other.id, "Rest") } }
        Unit
    }

    @Test
    fun `a booking whose category was deleted while it was edited goes to "Sonstiges"`() = runBlocking {
        val clothes = category(BookingKind.Expense, "Kleidung")
        ledger.deleteCategory(clothes)

        val id = ledger.create(BookingDraft(BookingKind.Expense, "20", LocalDate.parse("2026-10-23"), clothes))

        assertEquals(category(BookingKind.Expense, "Sonstiges"), ledger.get(id)?.categoryId)
    }

    @Test
    fun `categories without "Sonstiges", as a backup may bring them, get it back, and nothing else`() = runBlocking {
        database.ledger().insertCategories(
            listOf(LedgerCategory(kind = BookingKind.Expense, name = "Reisen"), LedgerCategory(kind = BookingKind.Expense, name = "sonstiges")),
        )

        val expenses = ledger.categories().first().filter { it.kind == BookingKind.Expense }

        assertEquals(listOf("Reisen" to false, "sonstiges" to true), expenses.map { it.name to it.isOther })
        ledger.deleteCategory(expenses.first().id)
        assertEquals(listOf("sonstiges"), ledger.categories().first().filter { it.kind == BookingKind.Expense }.map { it.name })
    }

    private suspend fun category(kind: BookingKind, name: String): Long =
        ledger.categories().first().single { it.kind == kind && it.name == name }.id

    private suspend fun book(kind: BookingKind, category: String, amount: String, date: String, description: String = ""): Long =
        ledger.create(BookingDraft(kind, amount, LocalDate.parse(date), category(kind, category), description))
}
