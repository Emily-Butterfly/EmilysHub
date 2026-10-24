package de.emilyshub.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.Repetition
import de.emilyshub.routines.RoutineDraft
import de.emilyshub.routines.Routines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class TagsTest {

    private val database = inMemoryDatabase()
    private val tags = Tags({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `tags are listed by name, each on a single line without surrounding spaces`() = runBlocking {
        tags.create("  Zuhause ")
        tags.create("Arbeit\n und  Schule")
        tags.create("Ärztliches")

        assertEquals(listOf("Arbeit und Schule", "Ärztliches", "Zuhause"), names())
    }

    @Test
    fun `a tag needs a name of up to 30 characters that differs from the others in more than upper and lower case`() {
        val others = listOf(Tag(id = 1, name = "Arbeit"))

        assertEquals(TagProblem.NameMissing, tagNameProblem(" ​ ", others))
        assertEquals(TagProblem.NameTooLong, tagNameProblem("a".repeat(31), others))
        assertEquals(null, tagNameProblem("🌱".repeat(30), others))
        assertEquals(TagProblem.NameTaken, tagNameProblem(" ARBEIT ", others))
        assertEquals(null, tagNameProblem("Arbeit", others = emptyList()))
    }

    @Test
    fun `a tag can be renamed, also to its own name in other case, but not to the name of another`() = runBlocking {
        val work = tags.create("arbeit")
        tags.create("Sport")

        tags.rename(work, "Arbeit")

        assertEquals(listOf("Arbeit", "Sport"), names())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { tags.rename(work, "sport") } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { tags.create("SPORT") } }
        assertEquals(listOf("Arbeit", "Sport"), names())
    }

    @Test
    fun `a tag deleted is taken off the routines that had it, which stay as they were`() = runBlocking {
        val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
        val routines = Routines({ database }, clock, Dispatchers.Unconfined)
        val work = tags.create("Arbeit")
        val sport = tags.create("Sport")
        val running = routines.create(
            RoutineDraft("Joggen", description = "", listOf(LocalTime.of(7, 30)), Repetition.Daily, tags = setOf(work, sport)),
        )

        tags.delete(work)

        assertEquals(listOf("Sport"), names())
        assertEquals(setOf(sport), routines.tagsOf(running))
        assertEquals(listOf("Joggen"), routines.inUse().first().map { it.name })
    }

    private suspend fun names() = tags.all().first().map { it.name }
}
