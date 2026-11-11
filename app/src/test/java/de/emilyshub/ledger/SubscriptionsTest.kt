package de.emilyshub.ledger

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.search.Search
import de.emilyshub.search.SearchArea
import de.emilyshub.todos.RecurrenceEnd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The Abos of the household book (SPEC.md §13): made, changed, cancelled and let run on, each with its last day to
 * cancel, linked to a fixed booking if wanted, what the running ones cost a month, and found by the search.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionsTest {

    private val database = inMemoryDatabase()

    /** Wednesday, 11 November 2026, 10:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-11-11T09:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val subscriptions = Subscriptions({ database }, clock, Dispatchers.Unconfined)
    private val ledger = Ledger({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `the running ones by their last day, with the days to it, the cancelled apart, and what they cost a month`() = runBlocking {
        save(SubscriptionDraft("Zeitung", amount = "30", interval = BookingInterval.Quarterly, cancelBy = date("2027-01-15")))
        save(SubscriptionDraft("Streaming", amount = "12", cancelBy = date("2026-11-30")))
        val probe = save(SubscriptionDraft("Probe-Abo", cancelBy = date("2026-12-01")))
        val gym = save(SubscriptionDraft("Fitness", amount = "40", cancelBy = date("2026-11-20")))
        subscriptions.cancel(gym, date("2026-12-31"))

        val overview = subscriptions.overview().first()
        assertEquals(listOf("Streaming", "Probe-Abo", "Zeitung"), overview.running.map { it.subscription.name })
        assertEquals(SubscriptionStatus.Due(date("2026-11-30"), daysLeft = 19), overview.running.first().status)
        assertEquals(listOf("Fitness"), overview.cancelled.map { it.subscription.name })
        // 12 € a month and 30 € a quarter; the one without amount and the one cancelled count not.
        assertEquals(2_200L, overview.perMonth)
        assertNull(overview.running.single { it.subscription.id == probe }.cost)
    }

    @Test
    fun `an Abo with a problem is refused, nothing saved`() = runBlocking {
        val refused = subscriptions.save(SubscriptionEditing.New, SubscriptionDraft("", cancelBy = null))

        assertEquals(Saving.Refused(setOf(SubscriptionProblem.NameMissing, SubscriptionProblem.CancelByMissing)), refused)
        assertTrue(subscriptions.overview().first().running.isEmpty())
    }

    @Test
    fun `a reckoned Abo moves on to its next term by itself, over several, and opens with the term that holds`() = runBlocking {
        val gym = save(
            SubscriptionDraft(
                "Fitness", reckoned = true, termEnd = date("2026-12-31"), notice = "1", noticeUnit = NoticeUnit.Months, renewal = "12",
            ),
        )
        assertEquals(date("2026-11-30"), lastDayOf(gym))

        clock.now = Instant.parse("2029-03-01T09:00:00Z")

        assertEquals(date("2029-11-30"), lastDayOf(gym))
        assertEquals(date("2029-12-31"), subscriptions.editing(gym)?.original?.termEnd)
    }

    @Test
    fun `a single date gone by has lapsed until a new one is saved`() = runBlocking {
        val paper = save(SubscriptionDraft("Zeitung", cancelBy = date("2026-11-20")))
        clock.now = Instant.parse("2026-11-21T09:00:00Z")
        assertEquals(SubscriptionStatus.Lapsed(date("2026-11-20")), statusOf(paper))

        change(paper) { it.copy(cancelBy = date("2027-11-20")) }

        assertEquals(SubscriptionStatus.Due(date("2027-11-20"), daysLeft = 364), statusOf(paper))
    }

    @Test
    fun `let run on, a reckoned one moves on to its next term, one by a single date waits for a new one`() = runBlocking {
        val gym = save(SubscriptionDraft("Fitness", reckoned = true, termEnd = date("2026-12-31"), notice = "14", noticeUnit = NoticeUnit.Days))
        val paper = save(SubscriptionDraft("Zeitung", cancelBy = date("2026-11-20")))

        subscriptions.runOn(gym)
        subscriptions.runOn(paper)

        assertEquals(date("2027-12-17"), lastDayOf(gym))
        assertEquals(SubscriptionStatus.RunsOn(date("2026-11-20")), statusOf(paper))
        change(paper) { it.copy(cancelBy = date("2027-11-20")) }
        assertEquals(date("2027-11-20"), lastDayOf(paper))
    }

    @Test
    fun `cancelled it ends on its date, by default the end of its term or its single date, and can be taken back`() = runBlocking {
        val gym = save(SubscriptionDraft("Fitness", reckoned = true, termEnd = date("2026-12-31"), notice = "1", noticeUnit = NoticeUnit.Months))
        val paper = save(SubscriptionDraft("Zeitung", cancelBy = date("2026-11-20")))
        assertEquals(date("2026-12-31"), item(gym).endIfCancelled)
        assertEquals(date("2026-11-20"), item(paper).endIfCancelled)

        subscriptions.cancel(gym, date("2026-12-31"))
        assertEquals(SubscriptionStatus.Cancelled(date("2026-12-31")), statusOf(gym))

        subscriptions.uncancel(gym)
        assertEquals(date("2026-11-30"), lastDayOf(gym))
    }

    @Test
    fun `linked to a fixed booking it has its amount and interval, and the booking at most one Abo`() = runBlocking {
        val rent = template("Streaming", "12,99", BookingInterval.Monthly)
        val draft = checkNotNull(subscriptions.draftFor(rent))
        assertEquals("Streaming", draft.name)
        assertEquals(rent, draft.recurringId)

        val streaming = save(draft.copy(cancelBy = date("2026-12-01")))
        assertEquals(SubscriptionCost(1_299, BookingInterval.Monthly), item(streaming).cost)
        assertEquals(streaming, subscriptions.ofRecurring(rent))
        assertTrue(subscriptions.linkable(except = null).first().none { it.id == rent })
        assertTrue(subscriptions.linkable(except = streaming).first().any { it.id == rent })

        val second = subscriptions.save(SubscriptionEditing.New, SubscriptionDraft("Noch eins", recurringId = rent, cancelBy = date("2026-12-01")))
        assertEquals(Saving.Refused(setOf(SubscriptionProblem.RecurringTaken)), second)

        // The booking changed, the Abo follows.
        val editing = checkNotNull(ledger.editingTemplate(rent))
        ledger.saveTemplate(editing, checkNotNull(editing.original).copy(amount = "13,99"))
        assertEquals(SubscriptionCost(1_399, BookingInterval.Monthly), item(streaming).cost)
    }

    @Test
    fun `a fixed booking without description gives the Abo the name of its category`() = runBlocking {
        val id = template("", "50", BookingInterval.Yearly)
        assertEquals("Freizeit", subscriptions.draftFor(id)?.name)
    }

    @Test
    fun `the fixed booking deleted, the Abo stays and keeps its amount and interval as its own`() = runBlocking {
        val rent = template("Zeitung", "30", BookingInterval.Quarterly)
        val paper = save(checkNotNull(subscriptions.draftFor(rent)).copy(cancelBy = date("2026-12-01")))

        ledger.deleteTemplate(rent)

        val kept = item(paper)
        assertNull(kept.subscription.recurringId)
        assertEquals(SubscriptionCost(3_000, BookingInterval.Quarterly), kept.cost)
        assertEquals("30", subscriptions.editing(paper)?.original?.amount)
    }

    @Test
    fun `cancelled with a fixed booking, the booking can end on the same day`() = runBlocking {
        val rent = template("Zeitung", "30", BookingInterval.Monthly)
        val paper = save(checkNotNull(subscriptions.draftFor(rent)).copy(cancelBy = date("2026-12-01")))

        subscriptions.cancel(paper, date("2027-01-31"))
        assertTrue(ledger.endTemplateOn(rent, date("2027-01-31")))

        assertEquals(RecurrenceEnd.OnDate(date("2027-01-31")), ledger.editingTemplate(rent)?.original?.end)
        // Not before its first booking.
        assertEquals(false, ledger.endTemplateOn(rent, date("2026-01-01")))
    }

    @Test
    fun `taken back, the cancellation lets its fixed booking run on again if it ended with it`() = runBlocking {
        val rent = template("Zeitung", "30", BookingInterval.Monthly)
        val paper = save(checkNotNull(subscriptions.draftFor(rent)).copy(cancelBy = date("2026-12-01")))
        subscriptions.cancel(paper, date("2027-01-31"))
        ledger.endTemplateOn(rent, date("2027-01-31"))

        subscriptions.uncancel(paper)

        assertEquals(RecurrenceEnd.Never, ledger.editingTemplate(rent)?.original?.end)
    }

    @Test
    fun `only fixed bookings of expenses are offered to link`() = runBlocking {
        val salary = ledger.categories().first().single { it.kind == BookingKind.Income && it.name == "Gehalt" }.id
        val income = ledger.createTemplate(
            RecurringDraft(BookingKind.Income, "2.500", salary, "Gehalt", BookingInterval.Monthly, day = 1, firstMonth = YearMonth.of(2026, 12)),
        )
        val expense = template("Zeitung", "30", BookingInterval.Monthly)

        assertEquals(listOf(expense), subscriptions.linkable(except = null).first().map { it.id }.filter { it == income || it == expense })
    }

    @Test
    fun `saved from its editor in February, a term on the 30th keeps ending on the 30th`() = runBlocking {
        val gym = save(SubscriptionDraft("Fitness", reckoned = true, termEnd = date("2027-01-30"), notice = "0", noticeUnit = NoticeUnit.Days, renewal = "1"))
        clock.now = Instant.parse("2027-02-10T09:00:00Z")
        change(gym) { it.copy(note = "Studio am Markt") }

        clock.now = Instant.parse("2027-03-10T09:00:00Z")
        assertEquals(date("2027-03-30"), lastDayOf(gym))
    }

    @Test
    fun `deleted, the Abo goes, its fixed booking stays`() = runBlocking {
        val rent = template("Zeitung", "30", BookingInterval.Monthly)
        val paper = save(checkNotNull(subscriptions.draftFor(rent)).copy(cancelBy = date("2026-12-01")))

        subscriptions.delete(paper)

        assertTrue(subscriptions.overview().first().running.isEmpty())
        assertTrue(ledger.templates().first().any { it.id == rent })
        assertNull(subscriptions.ofRecurring(rent))
    }

    @Test
    fun `the search finds Abos by name and note`() = runBlocking {
        val paper = save(SubscriptionDraft("Zeitung", note = "Kundennummer 4711, Kündigung per Brief", cancelBy = date("2026-12-01")))
        val search = Search({ database }, Dispatchers.Unconfined)

        assertEquals(listOf(paper), search.find("zeitung").single { it.area == SearchArea.Subscriptions }.hits.map { it.id })
        assertEquals("Zeitung", search.find("brief 4711").single { it.area == SearchArea.Subscriptions }.hits.single().title)
    }

    private fun date(text: String) = LocalDate.parse(text)

    private suspend fun save(draft: SubscriptionDraft): Long = when (val saved = subscriptions.save(SubscriptionEditing.New, draft)) {
        is Saving.Saved -> saved.id
        is Saving.Refused -> error("Refused: ${saved.problems}")
    }

    private suspend fun change(id: Long, change: (SubscriptionDraft) -> SubscriptionDraft) {
        val editing = checkNotNull(subscriptions.editing(id))
        val saved = subscriptions.save(editing, change(checkNotNull(editing.original)))
        check(saved is Saving.Saved) { "Refused: $saved" }
    }

    private suspend fun item(id: Long): SubscriptionItem =
        subscriptions.overview().first().let { it.running + it.cancelled }.single { it.subscription.id == id }

    private suspend fun statusOf(id: Long) = item(id).status

    private suspend fun lastDayOf(id: Long) = (statusOf(id) as SubscriptionStatus.Due).lastDay

    private suspend fun template(description: String, amount: String, interval: BookingInterval): Long {
        val leisure = ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == "Freizeit" }.id
        return ledger.createTemplate(
            RecurringDraft(BookingKind.Expense, amount, leisure, description, interval, day = 1, firstMonth = YearMonth.of(2026, 12)),
        )
    }
}
