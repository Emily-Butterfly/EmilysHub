package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Which day an entry of Tracking is for (SPEC.md §17, "Tag", "Nachtragen"). */
class EntryDayTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(berlin)

    @Test
    fun `until 04 00 a new entry is for the day before`() {
        assertEquals(LocalDate.parse("2026-11-14"), EntryDay.suggested(at("2026-11-15T00:30")))
        assertEquals(LocalDate.parse("2026-11-14"), EntryDay.suggested(at("2026-11-15T03:59")))
        assertEquals(LocalDate.parse("2026-11-15"), EntryDay.suggested(at("2026-11-15T04:00")))
        assertEquals(LocalDate.parse("2026-11-15"), EntryDay.suggested(at("2026-11-15T23:59")))
    }

    @Test
    fun `the 04 00 boundary holds by the clock on the wall when the clocks change`() {
        // Summer time begins at 02:00 on 29 March 2026: 02:30 does not exist and becomes 03:30.
        assertEquals(LocalDate.parse("2026-03-28"), EntryDay.suggested(at("2026-03-29T03:30")))
        assertEquals(LocalDate.parse("2026-03-29"), EntryDay.suggested(at("2026-03-29T04:00")))
        // Winter time begins at 03:00 on 25 October 2026: 02:30 comes twice, both before 04:00.
        val first = ZonedDateTime.ofLocal(LocalDateTime.parse("2026-10-25T02:30"), berlin, null).withEarlierOffsetAtOverlap()
        assertEquals(LocalDate.parse("2026-10-24"), EntryDay.suggested(first))
        assertEquals(LocalDate.parse("2026-10-24"), EntryDay.suggested(first.withLaterOffsetAtOverlap()))
        assertEquals(LocalDate.parse("2026-10-25"), EntryDay.suggested(at("2026-10-25T04:00")))
    }

    @Test
    fun `entries can be made up to seven days back, not for days to come`() {
        val today = LocalDate.parse("2026-11-15")

        assertTrue(EntryDay.canRecord(today, today))
        assertTrue(EntryDay.canRecord(LocalDate.parse("2026-11-08"), today))
        assertFalse(EntryDay.canRecord(LocalDate.parse("2026-11-07"), today))
        assertFalse(EntryDay.canRecord(LocalDate.parse("2026-11-16"), today))
        assertEquals(LocalDate.parse("2026-11-08"), EntryDay.earliest(today))
    }

    @Test
    fun `an entry recorded later than the day after is marked as made up`() {
        val day = LocalDate.parse("2026-11-12")

        assertFalse(EntryDay.isBackfilled(day, recordedOn = day))
        assertFalse(EntryDay.isBackfilled(day, recordedOn = LocalDate.parse("2026-11-13")))
        assertTrue(EntryDay.isBackfilled(day, recordedOn = LocalDate.parse("2026-11-14")))
    }
}
