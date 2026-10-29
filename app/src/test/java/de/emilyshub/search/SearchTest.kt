package de.emilyshub.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.notes.NoteDraft
import de.emilyshub.notes.Notes
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import de.emilyshub.tags.Tags
import de.emilyshub.todos.Due
import de.emilyshub.todos.Recurrence
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import de.emilyshub.wishes.WishDraft
import de.emilyshub.wishes.Wishes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The search over all areas (SPEC.md §8): routines, to-dos, wishes and notes at once, by their titles, descriptions,
 * the text of notes and tags, in whatever case; what is typed is a value to look for, never part of the query.
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val search = Search({ database }, Dispatchers.Unconfined)
    private val routines = Routines({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)
    private val tags = Tags({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `all areas are searched at once, by title, description, the text of notes and tags, grouped by area`() = runBlocking {
        val language = tags.create("Sprachen")
        routines.create(routine("Vokabeln", description = "Arabisch üben", tagIds = setOf(language)))
        todos.create(TodoDraft("Wörterbuch kaufen", description = "für Arabisch"))
        wishes.create(WishDraft("Kairo besuchen", tagIds = setOf(language)))
        notes.create(NoteDraft("Verben", "Präsens im Arabischen"))
        notes.create(NoteDraft("Einkauf", "Milch"))

        val found = search.find("arabisch")

        assertEquals(listOf(SearchArea.Routines, SearchArea.Todos, SearchArea.Notes), found.map { it.area })
        assertEquals(listOf("Vokabeln"), titles(found, SearchArea.Routines))
        assertEquals(listOf("Wörterbuch kaufen"), titles(found, SearchArea.Todos))
        assertEquals(emptyList<String>(), titles(found, SearchArea.Wishes))
        assertEquals(listOf("Verben"), titles(found, SearchArea.Notes))
        assertEquals(listOf("Kairo besuchen", "Vokabeln"), search.find("SPRACHEN").flatMap { it.hits }.map { it.title }.sorted())
    }

    @Test
    fun `umlauts are found in whatever case, and every word typed has to be there`() = runBlocking {
        notes.create(NoteDraft("Übungen", "für die Prüfung"))
        notes.create(NoteDraft("Übersicht", "Grammatik"))

        assertEquals(listOf("Übersicht", "Übungen"), titles(search.find("ÜB"), SearchArea.Notes))
        assertEquals(listOf("Übungen"), titles(search.find("übungen prüfung"), SearchArea.Notes))
        assertEquals(emptyList<String>(), titles(search.find("bar"), SearchArea.Notes))
    }

    @Test
    fun `text pasted with its umlauts in two parts is found as it reads, and so is a search typed so`() = runBlocking {
        notes.create(NoteDraft("Abfall", "Mu\u0308ll rausbringen"))
        notes.create(NoteDraft("Altglas", "Flaschen zum Container, auch die Öl\u200Bflasche"))

        assertEquals(listOf("Abfall"), titles(search.find("müll"), SearchArea.Notes))
        assertEquals(listOf("Abfall"), titles(search.find("Mu\u0308ll"), SearchArea.Notes))
        assertEquals(listOf("Altglas"), titles(search.find("ÖL"), SearchArea.Notes))
    }

    @Test
    fun `what is typed is looked for as written, wildcards and quotes too, and nothing typed finds nothing`() = runBlocking {
        notes.create(NoteDraft("Rabatt", "100% sicher"))
        notes.create(NoteDraft("Datei", "name_v2"))
        notes.create(NoteDraft("Sonstiges", "1000 Dinge, name v2"))

        assertEquals(listOf("Rabatt"), titles(search.find("100%"), SearchArea.Notes))
        assertEquals(listOf("Datei"), titles(search.find("_v"), SearchArea.Notes))
        assertEquals(emptyList<SearchGroup>(), search.find("' OR 1=1 --"))
        assertEquals(emptyList<SearchGroup>(), search.find("\\"))
        assertEquals(emptyList<SearchGroup>(), search.find("   "))
    }

    @Test
    fun `of a to-do coming again only the one now is found`() = runBlocking {
        val due = Due.On(LocalDate.parse("2026-10-01"))
        val rent = todos.create(TodoDraft("Miete überweisen", due = due, recurrence = Recurrence.Monthly(1)))
        todos.markDone(rent)

        val found = search.find("miete").single().hits.single()

        assertEquals(todos.get(rent)?.nextId, found.id)
    }

    private fun titles(found: List<SearchGroup>, area: SearchArea) = found.find { it.area == area }?.hits.orEmpty().map { it.title }

    private fun routine(name: String, description: String = "", tagIds: Set<Long> = emptySet()) =
        RoutineDraft(name, description, listOf(LocalTime.of(8, 0)), Repetition.Daily, tagIds = tagIds)
}
