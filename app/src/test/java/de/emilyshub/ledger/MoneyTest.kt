package de.emilyshub.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Amounts in euros and cents (SPEC.md §13): typed with a comma, shown as "1.234,56 €", reckoned in cents. */
class MoneyTest {

    @Test
    fun `typed with a comma, with or without cents and thousands dots`() {
        assertEquals(123_456L, Money.parse("1.234,56"))
        assertEquals(123_456L, Money.parse("1234,56"))
        assertEquals(1_200L, Money.parse("12"))
        assertEquals(1_250L, Money.parse("12,5"))
        assertEquals(1_250L, Money.parse(" 12,50 € "))
        assertEquals(5L, Money.parse("0,05"))
        assertEquals(0L, Money.parse("0"))
    }

    @Test
    fun `nothing else is an amount`() {
        listOf("", "abc", "12,505", "12.50", "1.23,45", "-5", "12,", ",5", "1,2,3", "12 34", "1.2345,00").forEach {
            assertNull(it, Money.parse(it))
        }
    }

    @Test
    fun `too large an amount is not one`() {
        assertEquals(Money.MAX, Money.parse("9.999.999,99"))
        assertNull(Money.parse("10.000.000"))
        assertNull(Money.parse("99999999999999999999999"))
    }

    @Test
    fun `shown with a comma and thousands dots, without rounding`() {
        assertEquals("1.234,56 €", Money.format(123_456))
        assertEquals("0,05 €", Money.format(5))
        assertEquals("12,00 €", Money.format(1_200))
        assertEquals("−12,50 €", Money.format(-1_250))
        assertEquals("1.000.000,00 €", Money.format(100_000_000))
        // A tenth plus a fifth is three tenths, to the cent.
        assertEquals("0,30 €", Money.format(Money.parse("0,1")!! + Money.parse("0,2")!!))
    }

    @Test
    fun `as typed again in the editor`() {
        assertEquals("1234,56", Money.typed(123_456))
        assertEquals("12", Money.typed(1_200))
        assertEquals("0,05", Money.typed(5))
    }
}
