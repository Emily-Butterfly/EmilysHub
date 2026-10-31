package de.emilyshub.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.search.Search
import de.emilyshub.search.SearchArea
import de.emilyshub.tags.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/** Notes have the shared tags too (SPEC.md §8): saved with them, found by them, a tag deleted taken off. */
@RunWith(AndroidJUnit4::class)
class NoteTagsTest {

    private val database = inMemoryDatabase()
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val notes = Notes({ database }, clock, Dispatchers.Unconfined)
    private val tags = Tags({ database }, Dispatchers.Unconfined)
    private val search = Search({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a note is saved with its tags, found by them, and a tag deleted is taken off`() = runBlocking {
        val language = tags.create("Sprachen")
        val work = tags.create("Arbeit")
        val gone = tags.create("Weg")
        tags.delete(gone)
        val verbs = notes.create(NoteDraft("Verben", tagIds = setOf(language, gone)))

        assertEquals(setOf(language), notes.tagIdsOf(verbs))
        assertEquals(listOf("Verben"), search.find("sprachen").single { it.area == SearchArea.Notes }.hits.map { it.title })

        notes.update(verbs, NoteDraft("Verben", tagIds = setOf(language, work)))
        tags.delete(language)
        assertEquals(setOf(work), notes.tagIdsOf(verbs))
    }
}
