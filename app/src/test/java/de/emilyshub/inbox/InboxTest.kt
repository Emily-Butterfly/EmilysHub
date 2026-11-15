package de.emilyshub.inbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class InboxTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-11-13T08:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val inbox = Inbox({ database }, clock, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a thought captured is in the inbox with when it was, the newest first`() = runBlocking {
        assertTrue(inbox.capture("Zahnarzt anrufen"))
        clock.now += Duration.ofMinutes(5)
        assertTrue(inbox.capture("  Geschenk für Mama  "))

        val entries = inbox.entries().first()
        assertEquals(listOf("Geschenk für Mama", "Zahnarzt anrufen"), entries.map { it.text })
        assertEquals(listOf(Instant.parse("2026-11-13T08:05:00Z"), Instant.parse("2026-11-13T08:00:00Z")), entries.map { it.capturedAt })
        assertEquals(2, inbox.count().first())
    }

    @Test
    fun `of two captured at the same moment, the later one is first`() = runBlocking {
        clock.now = Instant.parse("2026-11-13T08:00:00.500Z")
        inbox.capture("Erster")
        inbox.capture("Zweiter")

        assertEquals(listOf("Zweiter", "Erster"), inbox.entries().first().map { it.text })
    }

    @Test
    fun `nothing to see is not captured, nor more than 1000 characters`() = runBlocking {
        assertFalse(inbox.capture(" \n​ "))
        assertFalse(inbox.capture("a".repeat(1001)))
        assertTrue(inbox.capture("🌱".repeat(1000)))

        assertEquals(1, inbox.count().first())
    }

    @Test
    fun `an entry's text can be changed, on several lines, but not to nothing or too much`() = runBlocking {
        inbox.capture("Einkaufen")
        val id = inbox.entries().first().single().id

        assertTrue(inbox.change(id, "Einkaufen\nMilch\nBrot\n"))
        assertFalse(inbox.change(id, "  "))
        assertFalse(inbox.change(id, "a".repeat(1001)))

        assertEquals("Einkaufen\nMilch\nBrot", inbox.entries().first().single().text)
    }

    @Test
    fun `an entry deleted comes back as it was with undo`() = runBlocking {
        inbox.capture("Erstes")
        clock.now += Duration.ofMinutes(1)
        inbox.capture("Zweites")
        val first = inbox.entries().first().last()

        val deleted = inbox.delete(first.id)
        assertEquals(listOf("Zweites"), inbox.entries().first().map { it.text })

        inbox.restore(checkNotNull(deleted))
        assertEquals(listOf("Zweites", "Erstes"), inbox.entries().first().map { it.text })
        assertEquals(first, inbox.entry(first.id))
    }

    @Test
    fun `an entry sorted out is gone, and one gone meanwhile is no matter`() = runBlocking {
        inbox.capture("Steuer")
        val id = inbox.entries().first().single().id

        inbox.sortedOut(id)
        inbox.sortedOut(id)

        assertNull(inbox.entry(id))
        assertEquals(0, inbox.count().first())
        assertNull(inbox.delete(id))
    }

    @Test
    fun `sorted in, the first line is the title and the rest what follows`() {
        assertEquals(Sorting(1, "Steuer machen", ""), Sorting.of(1, "Steuer machen", titleMax = 80))
        assertEquals(
            Sorting(1, "Einkaufen", "Milch\nBrot"),
            Sorting.of(1, "Einkaufen\nMilch\nBrot", titleMax = 80),
        )
    }

    @Test
    fun `sorted in, what does not fit into the title goes first into the rest`() {
        assertEquals(
            Sorting(1, "Zahnarzt", "anrufen\nwegen Termin"),
            Sorting.of(1, "Zahnarzt anrufen\nwegen Termin", titleMax = 10),
        )
        // Cut where it fits, with no word to cut at.
        assertEquals(Sorting(1, "🌱🌱🌱", "🌱🌱"), Sorting.of(1, "🌱🌱🌱🌱🌱", titleMax = 3))
    }
}
