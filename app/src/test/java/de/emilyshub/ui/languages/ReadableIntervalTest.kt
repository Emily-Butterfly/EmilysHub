package de.emilyshub.ui.languages

import org.junit.Assert.assertEquals
import org.junit.Test

/** An answer's interval as its button tells it: days below a month, then months, from a year years. */
class ReadableIntervalTest {

    @Test
    fun `below a month in days, then in months, from a year in years`() {
        assertEquals(ReadableInterval(IntervalUnit.Days, 10), ReadableInterval.of(1))
        assertEquals(ReadableInterval(IntervalUnit.Days, 300), ReadableInterval.of(30))
        assertEquals(IntervalUnit.Months, ReadableInterval.of(31).unit)
        assertEquals(IntervalUnit.Months, ReadableInterval.of(364).unit)
        assertEquals(IntervalUnit.Years, ReadableInterval.of(365).unit)
        assertEquals(IntervalUnit.Years, ReadableInterval.of(36_500).unit)
    }

    @Test
    fun `one decimal below ten, none when whole, whole from ten on`() {
        assertEquals("8", ReadableInterval.of(8).number)
        assertEquals("1", ReadableInterval.of(31).number)
        assertEquals("1,5", ReadableInterval.of(46).number)
        assertEquals("4,5", ReadableInterval.of(137).number)
        assertEquals("12", ReadableInterval.of(364).number)
        assertEquals("1", ReadableInterval.of(365).number)
        assertEquals("2,7", ReadableInterval.of(1_000).number)
        assertEquals("100", ReadableInterval.of(36_500).number)
    }
}
