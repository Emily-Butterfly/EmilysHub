package de.emilyshub.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A budget as typed (SPEC.md §13): left empty, none; else an amount in euros with a comma, above zero and up to the
 * largest amount; the same rule for the dialogs and for the Haushaltsbuch, which refuses any other.
 */
class BudgetInputTest {

    @Test
    fun `an amount above zero is the budget, in cents`() {
        val input = BudgetInput("250,50")

        assertNull(input.problem)
        assertEquals(25_050L, input.budget)
    }

    @Test
    fun `left empty, there is no budget, and nothing keeps it from being set`() {
        val input = BudgetInput("  ")

        assertNull(input.problem)
        assertNull(input.budget)
    }

    @Test
    fun `no amount, or none above zero, keeps it from being set`() {
        assertEquals(BudgetProblem.AmountInvalid, BudgetInput("12,345").problem)
        assertEquals(BudgetProblem.AmountInvalid, BudgetInput("1.000.000.000").problem)
        assertEquals(BudgetProblem.NotAboveZero, BudgetInput("0,00").problem)
    }

    @Test
    fun `the Haushaltsbuch takes none, or one above zero up to the largest amount`() {
        assertTrue(BudgetInput.fits(null))
        assertTrue(BudgetInput.fits(Money.MAX))
        assertFalse(BudgetInput.fits(0))
        assertFalse(BudgetInput.fits(Money.MAX + 1))
    }
}
