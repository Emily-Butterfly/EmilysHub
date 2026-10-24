package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class TimeWindowTest {

    @Test
    fun `by default an occurrence can be caught up on for four hours`() {
        assertEquals(berlin("2026-10-23T12:00"), TimeWindow().endOf("2026-10-23", "08:00"))
    }

    @Test
    fun `a window runs past midnight into the next day`() {
        assertEquals(berlin("2026-10-24T02:00"), TimeWindow(hours = 4).endOf("2026-10-23", "22:00"))
        assertEquals(berlin("2026-10-24T08:00"), TimeWindow(hours = 24).endOf("2026-10-23", "08:00"))
    }

    @Test
    fun `until midnight, it ends as the occurrence's day does, however late it is due`() {
        val untilMidnight = TimeWindow(untilMidnight = true)

        assertEquals(berlin("2026-10-24T00:00"), untilMidnight.endOf("2026-10-23", "08:00"))
        assertEquals(berlin("2026-10-24T00:00"), untilMidnight.endOf("2026-10-23", "23:30"))
    }

    @Test
    fun `hours are hours as they pass, on the night the clocks spring forward`() {
        // 01:30 + 4 h: 02:00 becomes 03:00, so the clock shows 06:30.
        assertEquals(Instant.parse("2027-03-28T04:30:00Z"), TimeWindow(hours = 4).endOf("2027-03-28", "01:30"))
    }

    @Test
    fun `hours are hours as they pass, on the night the clocks fall back`() {
        // 01:30 + 4 h: 03:00 becomes 02:00, so the clock shows 04:30.
        assertEquals(Instant.parse("2026-10-25T03:30:00Z"), TimeWindow(hours = 4).endOf("2026-10-25", "01:30"))
    }

    @Test
    fun `until midnight, a day with a clock change is 23 or 25 hours long`() {
        val untilMidnight = TimeWindow(untilMidnight = true)

        assertEquals(berlin("2027-03-29T00:00"), untilMidnight.endOf("2027-03-28", "00:00"))
        assertEquals(berlin("2026-10-26T00:00"), untilMidnight.endOf("2026-10-25", "00:00"))
    }

    private fun TimeWindow.endOf(date: String, time: String): Instant = end(LocalDate.parse(date), LocalTime.parse(time), BERLIN)

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

        fun berlin(localDateTime: String): Instant = LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant()
    }
}
