package de.emilyshub.reminders

import de.emilyshub.reminders.PutOffChoice.By
import de.emilyshub.reminders.PutOffChoice.Tonight
import de.emilyshub.reminders.PutOffChoice.TomorrowMorning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** What "Später" offers a to-do (SPEC.md §3, #98), and when each choice comes back, in the phone's time zone. */
class PutOffChoiceTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val tenMinutes = By(Duration.ofMinutes(10))
    private val halfAnHour = By(Duration.ofMinutes(30))
    private val anHour = By(Duration.ofMinutes(60))

    @Test
    fun `before 19 00 a to-do's alarm offers all, its notification 30 min, Heute Abend and Morgen früh`() {
        val now = at("2026-10-23T18:59")

        assertEquals(listOf(tenMinutes, halfAnHour, anHour, Tonight, TomorrowMorning), PutOffChoice.forTodo(now, berlin))
        assertEquals(listOf(halfAnHour, Tonight, TomorrowMorning), PutOffChoice.forTodoNotification(now, berlin))
    }

    @Test
    fun `from 19 00 on Heute Abend is not offered, and the notification offers 1 h instead`() {
        val now = at("2026-10-23T19:00")

        assertEquals(listOf(tenMinutes, halfAnHour, anHour, TomorrowMorning), PutOffChoice.forTodo(now, berlin))
        assertEquals(listOf(halfAnHour, anHour, TomorrowMorning), PutOffChoice.forTodoNotification(now, berlin))
    }

    @Test
    fun `Heute Abend is 20 00 today, Morgen früh 08 00 tomorrow`() {
        val now = at("2026-10-23T09:15")

        assertEquals(at("2026-10-23T20:00"), Tonight.until(now, berlin))
        assertEquals(at("2026-10-24T08:00"), TomorrowMorning.until(now, berlin))
        assertEquals(at("2026-10-23T09:45"), halfAnHour.until(now, berlin))
    }

    @Test
    fun `just before midnight Morgen früh is the next day, just after it, before 04 00, the same day`() {
        assertEquals(at("2026-10-24T08:00"), TomorrowMorning.until(at("2026-10-23T23:59"), berlin))
        assertEquals(at("2026-10-24T08:00"), TomorrowMorning.until(at("2026-10-24T00:01"), berlin))
        assertEquals(at("2026-10-24T08:00"), TomorrowMorning.until(at("2026-10-24T03:59"), berlin))
        assertEquals(at("2026-10-25T08:00"), TomorrowMorning.until(at("2026-10-24T04:00"), berlin))
    }

    @Test
    fun `just after midnight Heute Abend is still offered, for 20 00 that day`() {
        val now = at("2026-10-24T00:30")

        assertEquals(listOf(tenMinutes, halfAnHour, anHour, Tonight, TomorrowMorning), PutOffChoice.forTodo(now, berlin))
        assertEquals(at("2026-10-24T20:00"), Tonight.until(now, berlin))
    }

    @Test
    fun `across the change to winter time the fixed times stay on the clock, the minutes count as they pass`() {
        // The night of 25 October 2026: 03:00 summer time becomes 02:00 winter time.
        val saturdayEvening = at("2026-10-24T22:00")
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), TomorrowMorning.until(saturdayEvening, berlin))
        assertEquals("an hour longer that night", Duration.ofHours(11), Duration.between(saturdayEvening, TomorrowMorning.until(saturdayEvening, berlin)))

        val beforeTheChange = Instant.parse("2026-10-25T00:30:00Z")
        assertEquals(Instant.parse("2026-10-25T01:30:00Z"), anHour.until(beforeTheChange, berlin))
    }

    @Test
    fun `across the change to summer time Morgen früh is 08 00 summer time`() {
        // The night of 29 March 2026: 02:00 winter time becomes 03:00 summer time.
        val saturdayEvening = at("2026-03-28T21:00")
        assertEquals(Instant.parse("2026-03-29T06:00:00Z"), TomorrowMorning.until(saturdayEvening, berlin))
    }

    @Test
    fun `each choice goes by a key of its own and back, and any other key is none`() {
        listOf(tenMinutes, halfAnHour, anHour, Tonight, TomorrowMorning).forEach { assertEquals(it, PutOffChoice.of(it.key)) }
        listOf("", "x", "-5", "abc10").forEach { assertNull(PutOffChoice.of(it)) }
    }

    private fun at(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(berlin).toInstant()
}
