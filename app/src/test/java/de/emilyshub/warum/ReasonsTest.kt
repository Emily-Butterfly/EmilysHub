package de.emilyshub.warum

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Saving
import de.emilyshub.data.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The Gründe of Mein Warum (SPEC.md §18): checked, laid out in the order the user gives them, and deleted. */
@RunWith(AndroidJUnit4::class)
class ReasonsTest {

    private val database = inMemoryDatabase()
    private val reasons = Reasons({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `a reason is kept with its text, without spaces at its edges, on its lines`() = runBlocking {
        val saved = reasons.save(null, ReasonDraft("  Lina\nund ihr Lachen  \n"))

        val id = (saved as Saving.Saved).id
        assertEquals(listOf("Lina\nund ihr Lachen"), texts())
        assertEquals(ReasonDraft("Lina\nund ihr Lachen"), reasons.editing(id))
    }

    @Test
    fun `an empty reason is refused, and one over 500 characters`() = runBlocking {
        assertEquals(Saving.Refused(setOf(ReasonProblem.Empty)), reasons.save(null, ReasonDraft(" \n​ ")))
        assertEquals(Saving.Refused(setOf(ReasonProblem.TextTooLong)), reasons.save(null, ReasonDraft("a".repeat(501))))
        assertEquals(Saving.Saved::class, reasons.save(null, ReasonDraft("🌱".repeat(500)))::class)

        assertEquals(1, texts().size)
    }

    @Test
    fun `a new reason comes on top`() = runBlocking {
        save("Lina")
        save("Das Meer")
        save("Mein Garten")

        assertEquals(listOf("Mein Garten", "Das Meer", "Lina"), texts())
    }

    @Test
    fun `a reason changed keeps its place`() = runBlocking {
        val lina = save("Lina")
        save("Das Meer")

        reasons.save(lina, ReasonDraft("Lina und Ben"))

        assertEquals(listOf("Das Meer", "Lina und Ben"), texts())
    }

    @Test
    fun `a reason moves forward and back by one, and stays at the ends`() = runBlocking {
        val lina = save("Lina")
        save("Das Meer")
        val garden = save("Mein Garten")

        reasons.forward(lina)
        assertEquals(listOf("Mein Garten", "Lina", "Das Meer"), texts())
        reasons.forward(lina)
        reasons.forward(lina)
        assertEquals(listOf("Lina", "Mein Garten", "Das Meer"), texts())

        reasons.back(garden)
        reasons.back(garden)
        assertEquals(listOf("Lina", "Das Meer", "Mein Garten"), texts())
    }

    @Test
    fun `a reason dragged goes where it is put, and a new one still comes on top`() = runBlocking {
        val lina = save("Lina")
        save("Das Meer")
        save("Mein Garten")
        save("Tee am Morgen")

        reasons.move(lina, 1)
        assertEquals(listOf("Tee am Morgen", "Lina", "Mein Garten", "Das Meer"), texts())
        reasons.move(lina, 10)
        assertEquals(listOf("Tee am Morgen", "Mein Garten", "Das Meer", "Lina"), texts())

        save("Musik")
        assertEquals("Musik", texts().first())
    }

    @Test
    fun `a reason deleted is gone, and the others keep their order`() = runBlocking {
        save("Lina")
        val sea = save("Das Meer")
        save("Mein Garten")

        reasons.delete(sea)

        assertEquals(listOf("Mein Garten", "Lina"), texts())
        assertNull(reasons.editing(sea))
        // Changed once gone, it is not made anew.
        assertEquals(Saving.Refused(setOf(ReasonProblem.Gone)), reasons.save(sea, ReasonDraft("Das Meer")))
        assertEquals(2, texts().size)
    }

    private suspend fun save(text: String): Long = (reasons.save(null, ReasonDraft(text)) as Saving.Saved).id

    private suspend fun texts() = reasons.board().first().map { it.text }
}
