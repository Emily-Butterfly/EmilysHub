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
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Fixed bookings of the household book (SPEC.md §13): templates such as rent, salary or subscriptions, entered on their
 * day by themselves, and made up for when the app did not run then, each exactly once.
 */
@RunWith(AndroidJUnit4::class)
class RecurringBookingsTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the dates of each interval, on the last day of shorter months, in leap years too`() {
        fun dates(interval: BookingInterval, day: Int, first: String, through: String) =
            schedule(interval, day, YearMonth.parse(first)).takeWhile { !it.isAfter(LocalDate.parse(through)) }.map(LocalDate::toString).toList()

        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"), dates(BookingInterval.Monthly, 31, "2026-01", "2026-04-30"))
        assertEquals(listOf("2028-01-30", "2028-02-29", "2028-03-30"), dates(BookingInterval.Monthly, 30, "2028-01", "2028-03-31"))
        assertEquals(listOf("2026-01-15", "2026-04-15", "2026-07-15", "2026-10-15"), dates(BookingInterval.Quarterly, 15, "2026-01", "2026-12-31"))
        assertEquals(listOf("2026-03-01", "2026-09-01", "2027-03-01"), dates(BookingInterval.HalfYearly, 1, "2026-03", "2027-08-31"))
        assertEquals(listOf("2028-02-29", "2029-02-28", "2030-02-28"), dates(BookingInterval.Yearly, 29, "2028-02", "2030-12-31"))
    }

    @Test
    fun `entered on its day, not before`() = runBlocking {
        val rent = template(BookingKind.Expense, "Wohnen", "850", BookingInterval.Monthly, day = 24, first = "2026-10", "Miete")

        ledger.enterDue()
        assertEquals(emptyList<String>(), entered())

        at("2026-10-24T00:01")
        ledger.enterDue()
        assertEquals(listOf("2026-10-24 Miete 85000"), entered())
        assertEquals(rent, ledger.month(YearMonth.of(2026, 10)).first().bookings.single().booking.recurringId)
        assertEquals(LocalDate.parse("2026-11-24"), ledger.templates().first().single().next)
    }

    @Test
    fun `made up after months without the app, each exactly once`() = runBlocking {
        template(BookingKind.Income, "Gehalt", "2.500", BookingInterval.Monthly, day = 1, first = "2026-07", "Gehalt")

        ledger.enterDue()
        ledger.enterDue()

        assertEquals(listOf("2026-07-01", "2026-08-01", "2026-09-01", "2026-10-01"), entered().map { it.substringBefore(' ') })
    }

    @Test
    fun `one entered is changed or deleted in its month without the template, and a deleted one does not come again`() = runBlocking {
        template(BookingKind.Expense, "Freizeit", "9,99", BookingInterval.Monthly, day = 5, first = "2026-10", "Streaming")
        ledger.enterDue()
        val entry = ledger.month(YearMonth.of(2026, 10)).first().bookings.single().booking

        ledger.update(entry.id, checkNotNull(ledger.draftOf(entry.id)).copy(amount = "12,99"))
        assertEquals("9,99", ledger.templates().first().single().draft.amount)

        ledger.delete(entry.id)
        ledger.enterDue()
        assertEquals(emptyList<String>(), entered())
    }

    @Test
    fun `a template changed holds from the next entry on, and one deleted brings no more, those entered staying`() = runBlocking {
        val id = template(BookingKind.Expense, "Freizeit", "9,99", BookingInterval.Monthly, day = 5, first = "2026-10", "Streaming")
        ledger.enterDue()

        val changed = ledger.templates().first().single().draft.copy(amount = "11,99")
        ledger.updateTemplate(id, changed)
        at("2026-11-05T08:00")
        ledger.enterDue()
        assertEquals(listOf("2026-10-05 Streaming 999", "2026-11-05 Streaming 1199"), entered())

        ledger.deleteTemplate(id)
        at("2026-12-05T08:00")
        ledger.enterDue()
        assertEquals(2, entered().size)
        assertEquals(emptyList<Any>(), ledger.templates().first())
    }

    @Test
    fun `a template needs an amount above zero, a category, and a day of the month`() = runBlocking {
        val food = ledger.categories().first().first { it.name == "Lebensmittel" }.id
        val draft = RecurringDraft(BookingKind.Expense, "5", food, "", BookingInterval.Monthly, 1, YearMonth.of(2026, 10))

        assertEquals(emptySet<RecurringProblem>(), draft.problems)
        assertEquals(setOf(RecurringProblem.AmountNotAboveZero), draft.copy(amount = "0").problems)
        assertEquals(setOf(RecurringProblem.DayOutOfRange), draft.copy(day = 32).problems)
        assertEquals(setOf(RecurringProblem.DayOutOfRange), draft.copy(day = 0).problems)
        assertEquals(setOf(RecurringProblem.CategoryMissing), draft.copy(categoryId = null).problems)
    }

    @Test
    fun `a category deleted takes its templates to "Sonstiges" too`() = runBlocking {
        val clothes = ledger.categories().first().first { it.name == "Kleidung" }.id
        template(BookingKind.Expense, "Kleidung", "30", BookingInterval.Yearly, day = 1, first = "2027-01", "Winterjacke")

        ledger.deleteCategory(clothes)

        assertEquals("Sonstiges", ledger.templates().first().single().category.name)
        assertNull(ledger.templates().first().single().draft.categoryId?.takeIf { it == clothes })
    }

    private suspend fun template(
        kind: BookingKind,
        category: String,
        amount: String,
        interval: BookingInterval,
        day: Int,
        first: String,
        description: String,
    ): Long {
        val id = ledger.categories().first().single { it.kind == kind && it.name == category }.id
        return ledger.createTemplate(RecurringDraft(kind, amount, id, description, interval, day, YearMonth.parse(first)))
    }

    /** All bookings entered, "2026-10-24 Miete 85000", by date. */
    private suspend fun entered(): List<String> =
        (2026..2027).flatMap { year -> (1..12).flatMap { ledger.month(YearMonth.of(year, it)).first().bookings } }
            .sortedBy { it.booking.date }
            .map { "${it.booking.date} ${it.booking.description} ${it.booking.amountCents}" }

    private fun at(localDateTime: String) {
        clock.now = LocalDateTime.parse(localDateTime).atZone(clock.zone).toInstant()
    }
}
