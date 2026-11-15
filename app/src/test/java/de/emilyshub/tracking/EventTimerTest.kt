package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The pulse timer of an event (SPEC.md §17, "Ereignisse"), as pure logic. */
class EventTimerTest {

    private val at = Instant.parse("2026-11-15T09:00:00Z")
    private val ended = Instant.parse("2026-11-15T09:12:00Z")
    private val event = TrackingEvent(kind = EventKind.Shower, at = at)

    private fun after(seconds: Long) = ended.plusSeconds(seconds)

    @Test
    fun `the pulses are due right after, then after five and ten minutes`() {
        assertEquals(ended, EventTimer.dueAt(ended, EventPulse.After))
        assertEquals(after(300), EventTimer.dueAt(ended, EventPulse.Five))
        assertEquals(after(600), EventTimer.dueAt(ended, EventPulse.Ten))
    }

    @Test
    fun `the next pulse is the first not entered yet`() {
        val running = event.copy(endedAt = ended)
        assertEquals(EventPulse.After, EventTimer.next(running))
        assertEquals(EventPulse.Five, EventTimer.next(running.copy(pulseAfter = 90)))
        assertEquals(EventPulse.Ten, EventTimer.next(running.copy(pulseAfter = 90, pulseFive = 80)))
        assertNull(EventTimer.next(running.copy(pulseAfter = 90, pulseFive = 80, pulseTen = 75)))
    }

    @Test
    fun `the timer runs from the end of the event until the pulse after ten minutes, at the longest a while after`() {
        assertFalse("not ended yet", EventTimer.runs(event, at.plusSeconds(60)))
        val running = event.copy(endedAt = ended)
        assertTrue(EventTimer.runs(running, ended))
        assertTrue(EventTimer.runs(running, after(600)))
        assertTrue(EventTimer.runs(running, after(600 + EventTimer.GRACE.seconds - 1)))
        assertFalse("the ten minutes long past", EventTimer.runs(running, after(600 + EventTimer.GRACE.seconds)))
        assertFalse("all entered", EventTimer.runs(running.copy(pulseAfter = 90, pulseFive = 80, pulseTen = 75), after(400)))
    }

    @Test
    fun `due once its time has come`() {
        assertFalse(EventTimer.isDue(ended, EventPulse.Five, after(299)))
        assertTrue(EventTimer.isDue(ended, EventPulse.Five, after(300)))
        assertTrue(EventTimer.isDue(ended, EventPulse.After, ended))
    }

    @Test
    fun `water temperature only for water`() {
        assertTrue(EventKind.Shower.water)
        assertTrue(EventKind.Bath.water)
        assertFalse(EventKind.Sauna.water)
        assertFalse(EventKind.LongStanding.water)
    }
}
