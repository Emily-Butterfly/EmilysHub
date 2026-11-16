package de.emilyshub.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The monthly questionnaire FUNCAP55 (SPEC.md §17, "Monatsfragebogen"): its items, its scores and when it is due, as pure logic. */
class FuncapTest {

    private val today = LocalDate.parse("2026-11-16")

    @Test
    fun `55 items in eight domains, numbered in order`() {
        assertEquals(listOf('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'), Funcap.DOMAINS.map { it.code })
        assertEquals(listOf(7, 7, 5, 6, 7, 7, 6, 10), Funcap.DOMAINS.map { it.items.size })
        assertEquals((1..55).toList(), Funcap.DOMAINS.flatMap { domain -> domain.items.map { it.number } })
        assertEquals(7, Funcap.ANSWERS.size)
    }

    @Test
    fun `a domain's score is the mean of its answered items`() {
        // A: items 1–7; two of them answered.
        val score = Funcap.score(mapOf(1 to 6, 2 to 3))
        assertEquals(4.5, score.domains.getValue('A')!!, 1e-9)
        assertNull(score.domains.getValue('B'))
        assertNull(score.total)
    }

    @Test
    fun `the total is the mean of the eight domains, only with all of them`() {
        // One item in each domain: A 6, B 5, … H 6 (the first item of each).
        val firsts = Funcap.DOMAINS.map { it.items.first().number }
        val values = listOf(6, 5, 4, 3, 2, 1, 0, 6)
        val score = Funcap.score(firsts.zip(values).toMap())
        assertEquals(values.average(), score.total!!, 1e-9)
        assertEquals(0.0, score.domains.getValue('G')!!, 1e-9)

        val withoutH = Funcap.score(firsts.zip(values).toMap() - firsts.last())
        assertNull(withoutH.domains.getValue('H'))
        assertNull(withoutH.total)
    }

    @Test
    fun `answers off the scale or of no item do not count`() {
        val score = Funcap.score(mapOf(1 to 7, 2 to -1, 3 to 2, 56 to 6))
        assertEquals(2.0, score.domains.getValue('A')!!, 1e-9)
        assertEquals(1, score.answered)
    }

    @Test
    fun `the domain of an item`() {
        assertEquals('A', Funcap.domainOf(7)?.code)
        assertEquals('B', Funcap.domainOf(8)?.code)
        assertEquals('H', Funcap.domainOf(55)?.code)
        assertNull(Funcap.domainOf(0))
        assertNull(Funcap.domainOf(56))
    }

    @Test
    fun `due 28 days after the last, and at once without any`() {
        assertTrue(Funcap.due(lastCompleted = null, today = today))
        assertFalse(Funcap.due(today.minusDays(27), today))
        assertTrue(Funcap.due(today.minusDays(28), today))
    }

    @Test
    fun `a draft older than 7 days is stale`() {
        assertFalse(Funcap.stale(startedOn = today.minusDays(7), today = today))
        assertTrue(Funcap.stale(startedOn = today.minusDays(8), today = today))
        assertFalse(Funcap.stale(startedOn = today, today = today))
    }
}
