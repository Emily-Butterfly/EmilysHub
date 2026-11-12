package de.emilyshub.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * When an Abo reminds to cancel it (#80): at 21:00 on the first day of its lead and again on its last day, made up for
 * later while that is not over, never after, and never once cancelled or let run on; reckoned, in the term that holds.
 */
class SubscriptionReminderTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun date(text: String) = LocalDate.parse(text)

    private fun at(text: String): Instant = LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun onDate(cancelBy: String, lead: Int = 14) =
        Subscription.of(1, "Streaming", Deadline.OnDate(date(cancelBy))).copy(leadDays = lead)

    private val gym = Subscription.of(2, "Fitness", Deadline.Term(date("2026-12-31"), Notice(1, NoticeUnit.Months), renewalMonths = 12))

    @Test
    fun `it notifies at nine in the evening on the first day of its lead and again on its last day`() {
        val reminder = checkNotNull(onDate("2026-11-30").reminderOn(date("2026-11-12"), zone))

        assertEquals(date("2026-11-16"), reminder.from)
        assertEquals(listOf(at("2026-11-16T21:00"), at("2026-11-30T21:00")), reminder.moments(zone))
        assertEquals(at("2026-12-01T00:00"), reminder.ends)
    }

    @Test
    fun `without lead it notifies once, on its last day`() {
        val reminder = checkNotNull(onDate("2026-11-30", lead = 0).reminderOn(date("2026-11-12"), zone))

        assertEquals(listOf(at("2026-11-30T21:00")), reminder.moments(zone))
    }

    @Test
    fun `the alarm is set for each moment in turn, then for its end, and none after a single date`() {
        val streaming = onDate("2026-11-30")

        assertEquals(at("2026-11-16T21:00"), streaming.nextAlarm(at("2026-11-12T10:00"), zone))
        assertEquals(at("2026-11-30T21:00"), streaming.nextAlarm(at("2026-11-16T21:00"), zone))
        assertEquals(at("2026-12-01T00:00"), streaming.nextAlarm(at("2026-11-30T21:00"), zone))
        assertNull(streaming.nextAlarm(at("2026-12-01T00:00"), zone))
    }

    @Test
    fun `reckoned, the alarm goes on to the next term once one is over`() {
        // Last day of the term ending on 31 December: 30 November.
        assertEquals(at("2026-11-30T21:00"), gym.nextAlarm(at("2026-11-16T21:00"), zone))
        assertEquals(at("2027-11-16T21:00"), gym.nextAlarm(at("2026-12-01T00:00"), zone))
    }

    @Test
    fun `what came due while the phone was off still comes, but not once its last day is over`() {
        val streaming = onDate("2026-11-30")

        // Off from the evening before the first day of its lead to the next morning.
        assertEquals(date("2026-11-30"), streaming.reminderDue(at("2026-11-15T20:00"), at("2026-11-17T08:00"), zone)?.lastDay)
        // Nothing in between.
        assertNull(streaming.reminderDue(at("2026-11-17T08:00"), at("2026-11-20T08:00"), zone))
        // Off over its last day: too late.
        assertNull(streaming.reminderDue(at("2026-11-30T20:00"), at("2026-12-01T08:00"), zone))
    }

    @Test
    fun `reckoned and off over its last day, the next term's comes in its own time`() {
        assertNull(gym.reminderDue(at("2026-11-30T20:00"), at("2026-12-01T08:00"), zone))
        assertEquals(date("2027-11-30"), gym.reminderDue(at("2027-11-16T20:00"), at("2027-11-16T21:30"), zone)?.lastDay)
    }

    @Test
    fun `made or changed within its lead, it notifies first on its last day, not for the time gone by`() {
        val streaming = onDate("2026-11-30").copy(remindsFrom = at("2026-11-20T10:00"))

        assertEquals(at("2026-11-30T21:00"), streaming.nextAlarm(at("2026-11-12T10:00"), zone))
        assertNull(streaming.reminderDue(at("2026-11-12T10:00"), at("2026-11-20T10:00"), zone))
        assertEquals(date("2026-11-30"), streaming.reminderDue(at("2026-11-12T10:00"), at("2026-11-30T21:00"), zone)?.lastDay)
    }

    @Test
    fun `cancelled or let run on, it reminds no more`() {
        val cancelled = onDate("2026-11-30").copy(endsOn = date("2026-11-30"))
        val runsOn = onDate("2026-11-30").copy(runsOn = true)

        for (subscription in listOf(cancelled, runsOn)) {
            assertNull(subscription.reminderOn(date("2026-11-20"), zone))
            assertNull(subscription.nextAlarm(at("2026-11-12T10:00"), zone))
            assertNull(subscription.reminderDue(at("2026-11-15T20:00"), at("2026-11-17T08:00"), zone))
        }
    }

    @Test
    fun `it is due from its first moment until its last day is over, and on Heute from the first day of its lead`() {
        val reminder = checkNotNull(onDate("2026-11-30").reminderOn(date("2026-11-16"), zone))

        assertFalse(reminder.dueAt(at("2026-11-16T20:59"), zone))
        assertTrue(reminder.dueAt(at("2026-11-16T21:00"), zone))
        assertTrue(reminder.dueAt(at("2026-11-30T23:59"), zone))
        assertFalse(reminder.dueAt(at("2026-12-01T00:00"), zone))

        assertFalse(reminder.showsOn(date("2026-11-15")))
        assertTrue(reminder.showsOn(date("2026-11-16")))
        assertTrue(reminder.showsOn(date("2026-11-30")))
        assertFalse(reminder.showsOn(date("2026-12-01")))
    }
}
