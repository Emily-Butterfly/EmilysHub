package de.emilyshub.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The terms of an Abo (SPEC.md §13): the last day to cancel it, a single date or reckoned from the end of its term, its
 * notice and its renewal; how it stands on a day; what the running ones cost a month; and the checks of its editor.
 */
class SubscriptionTermsTest {

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `the last day to cancel is the notice before the end of the term, in days, weeks or months`() {
        val end = date("2027-03-15")
        assertEquals(date("2027-03-01"), Notice(14, NoticeUnit.Days).before(end))
        assertEquals(date("2027-02-15"), Notice(4, NoticeUnit.Weeks).before(end))
        assertEquals(date("2026-12-15"), Notice(3, NoticeUnit.Months).before(end))
        assertEquals(end, Notice(0, NoticeUnit.Days).before(end))
    }

    @Test
    fun `a term ending on the last of a month gives notice by the last of a month`() {
        assertEquals(date("2027-03-31"), Notice(1, NoticeUnit.Months).before(date("2027-04-30")))
        assertEquals(date("2027-02-28"), Notice(1, NoticeUnit.Months).before(date("2027-03-31")))
        // Not the last of its month: the same day, or the last of a shorter month.
        assertEquals(date("2027-02-28"), Notice(1, NoticeUnit.Months).before(date("2027-03-30")))
    }

    @Test
    fun `a term renews by its months, the last of a month staying the last`() {
        val term = Deadline.Term(date("2027-02-28"), Notice(1, NoticeUnit.Months), renewalMonths = 12)
        assertEquals(date("2028-02-29"), term.next().termEnd)
        assertEquals(date("2027-03-31"), Deadline.Term(date("2027-02-28"), Notice(1, NoticeUnit.Months), 1).next().termEnd)
        assertEquals(date("2027-07-15"), Deadline.Term(date("2027-01-15"), Notice(1, NoticeUnit.Months), 6).next().termEnd)
    }

    @Test
    fun `a term whose last day went by moves on to the next, over several if need be`() {
        val term = Deadline.Term(date("2026-12-31"), Notice(3, NoticeUnit.Months), renewalMonths = 12)
        assertEquals(date("2026-09-30"), term.lastDay)

        // On its last day it still holds.
        assertEquals(term, term.current(date("2026-09-30")))
        assertEquals(date("2027-12-31"), term.current(date("2026-10-01")).termEnd)
        assertEquals(date("2029-12-31"), term.current(date("2029-01-10")).termEnd)
    }

    @Test
    fun `a running Abo is due by its last day, so many days on`() {
        val single = subscription(Deadline.OnDate(date("2026-11-30")))
        assertEquals(SubscriptionStatus.Due(date("2026-11-30"), daysLeft = 19), single.statusOn(date("2026-11-11")))
        assertEquals(SubscriptionStatus.Due(date("2026-11-30"), daysLeft = 0), single.statusOn(date("2026-11-30")))

        val reckoned = subscription(Deadline.Term(date("2026-12-31"), Notice(1, NoticeUnit.Months), 12))
        assertEquals(SubscriptionStatus.Due(date("2027-11-30"), daysLeft = 1), reckoned.statusOn(date("2027-11-29")))
    }

    @Test
    fun `a single date gone by has lapsed until a new one is set, as has one let run on`() {
        val single = subscription(Deadline.OnDate(date("2026-11-30")))
        assertEquals(SubscriptionStatus.Lapsed(date("2026-11-30")), single.statusOn(date("2026-12-01")))
        assertEquals(SubscriptionStatus.RunsOn(date("2026-11-30")), single.copy(runsOn = true).statusOn(date("2026-11-11")))
    }

    @Test
    fun `a cancelled Abo ends on its date, whatever its deadline`() {
        val cancelled = subscription(Deadline.OnDate(date("2026-11-30"))).copy(endsOn = date("2026-12-31"))
        assertEquals(SubscriptionStatus.Cancelled(date("2026-12-31")), cancelled.statusOn(date("2027-05-01")))
    }

    @Test
    fun `the running Abos cost so much a month, those without an amount not counting`() {
        assertEquals(
            1_000L + 300L + 250L + 100L,
            perMonth(
                listOf(
                    SubscriptionCost(1_000, BookingInterval.Monthly),
                    SubscriptionCost(900, BookingInterval.Quarterly),
                    SubscriptionCost(1_500, BookingInterval.HalfYearly),
                    SubscriptionCost(1_200, BookingInterval.Yearly),
                ),
            ),
        )
        // Rounded once, at the end, half up.
        assertEquals(167L, perMonth(listOf(SubscriptionCost(1_000, BookingInterval.Yearly), SubscriptionCost(1_000, BookingInterval.Yearly))))
        assertEquals(0L, perMonth(emptyList()))
    }

    @Test
    fun `an Abo needs a name, a deadline, and what is typed must be whole`() {
        val fine = SubscriptionDraft(name = "Streaming", cancelBy = date("2026-12-01"))
        assertEquals(emptySet<SubscriptionProblem>(), fine.problems)

        assertEquals(setOf(SubscriptionProblem.NameMissing), fine.copy(name = " ​ ").problems)
        assertEquals(setOf(SubscriptionProblem.NameTooLong), fine.copy(name = "x".repeat(SubscriptionDraft.NAME_MAX + 1)).problems)
        assertEquals(setOf(SubscriptionProblem.NoteTooLong), fine.copy(note = "x".repeat(SubscriptionDraft.NOTE_MAX + 1)).problems)
        assertEquals(setOf(SubscriptionProblem.CancelByMissing), fine.copy(cancelBy = null).problems)
        assertEquals(setOf(SubscriptionProblem.AmountInvalid), fine.copy(amount = "9,999").problems)
        assertEquals(setOf(SubscriptionProblem.AmountNotAboveZero), fine.copy(amount = "0").problems)
        assertEquals(setOf(SubscriptionProblem.LeadOutOfRange), fine.copy(lead = "").problems)
        assertEquals(setOf(SubscriptionProblem.LeadOutOfRange), fine.copy(lead = "366").problems)
        assertEquals(emptySet<SubscriptionProblem>(), fine.copy(amount = "9,99", lead = "0").problems)
    }

    @Test
    fun `a reckoned deadline needs the end of the term, a notice in range and a renewal of at least a month`() {
        val term = SubscriptionDraft(name = "Fitness", reckoned = true, termEnd = date("2026-12-31"), notice = "3", noticeUnit = NoticeUnit.Months, renewal = "12")
        assertEquals(emptySet<SubscriptionProblem>(), term.problems)
        assertEquals(Deadline.Term(date("2026-12-31"), Notice(3, NoticeUnit.Months), 12), term.deadline)
        // The single date is not asked for.
        assertTrue(term.cancelBy == null)

        assertEquals(setOf(SubscriptionProblem.TermEndMissing), term.copy(termEnd = null).problems)
        assertEquals(setOf(SubscriptionProblem.NoticeOutOfRange), term.copy(notice = "25").problems)
        assertEquals(emptySet<SubscriptionProblem>(), term.copy(notice = "25", noticeUnit = NoticeUnit.Weeks).problems)
        assertEquals(setOf(SubscriptionProblem.NoticeOutOfRange), term.copy(notice = "").problems)
        assertEquals(setOf(SubscriptionProblem.RenewalOutOfRange), term.copy(renewal = "0").problems)
        assertEquals(setOf(SubscriptionProblem.RenewalOutOfRange), term.copy(renewal = "121").problems)
    }

    @Test
    fun `linked to a fixed booking, its amount is that of the booking and not checked`() {
        val linked = SubscriptionDraft(name = "Miete", cancelBy = date("2026-12-01"), amount = "kaputt", recurringId = 4)
        assertEquals(emptySet<SubscriptionProblem>(), linked.problems)
    }

    private fun subscription(deadline: Deadline) = Subscription.of(1, "Abo", deadline)
}
