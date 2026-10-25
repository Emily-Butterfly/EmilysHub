package de.emilyshub.wishes

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.ChecklistDraftItem
import de.emilyshub.tags.Tags
import de.emilyshub.todos.TodoDraft
import de.emilyshub.todos.Todos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Goals (SPEC.md §6): wishes with subgoals to any depth and to-dos hanging under them, done once all of those are, and
 * then in the review if at the top.
 */
@RunWith(AndroidJUnit4::class)
class GoalsTest {

    private val database = inMemoryDatabase()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val today = LocalDate.parse("2026-10-23")
    private val wishes = Wishes({ database }, clock, Dispatchers.Unconfined)
    private val todos = Todos({ database }, clock, Dispatchers.Unconfined)
    private val tags = Tags({ database }, Dispatchers.Unconfined)

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `subgoals hang under their goal to any depth, and the wishlist shows the goals at the top with them`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val vocabulary = wishes.create(WishDraft("Wortschatz", parentId = arabic))
        wishes.create(WishDraft("Verben", parentId = vocabulary))

        val listed = wishes.current().first()

        assertEquals(listOf("Arabisch A2"), listed.map { it.wish.title })
        assertEquals("Verben", listed.single().subgoals.single().subgoals.single().wish.title)
    }

    @Test
    fun `a goal cannot hang under itself or under one of its subgoals`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val vocabulary = wishes.create(WishDraft("Wortschatz", parentId = arabic))
        val verbs = wishes.create(WishDraft("Verben", parentId = vocabulary))

        for (parent in listOf(arabic, vocabulary, verbs)) {
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { wishes.update(arabic, WishDraft("Arabisch A2", parentId = parent)) }
            }
        }
        assertNull(wishes.get(arabic)?.parentId)
    }

    @Test
    fun `a goal is offered to hang under any goal but itself and those below it, in the order of the tree`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val vocabulary = wishes.create(WishDraft("Wortschatz", parentId = arabic))
        wishes.create(WishDraft("Verben", parentId = vocabulary))
        val trip = wishes.create(WishDraft("Reise"))
        val flights = wishes.create(WishDraft("Flüge", parentId = trip))

        assertEquals(
            listOf(GoalChoice(trip, "Reise", 0), GoalChoice(flights, "Flüge", 1)),
            wishes.goalChoices(excluding = arabic).first(),
        )
        assertEquals(listOf("Arabisch A2", "Wortschatz", "Verben", "Reise", "Flüge"), wishes.goalChoices().first().map { it.title })
    }

    @Test
    fun `a goal is done today once all its parts are, up to the top, and then in the review`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val a1 = wishes.create(WishDraft("A1 abschließen", parentId = arabic))
        val book = todos.create(TodoDraft("Lehrbuch Kapitel 1–6", goalId = a1))
        val exam = todos.create(TodoDraft("A2-Prüfung anmelden", goalId = arabic))

        todos.markDone(book)
        assertEquals(WishStatus.Done to today, wishes.get(a1)?.let { it.status to it.doneOn })
        assertEquals(WishStatus.Open, wishes.get(arabic)?.status)

        todos.markDone(exam)
        assertEquals(WishStatus.Done to today, wishes.get(arabic)?.let { it.status to it.doneOn })
        assertEquals(listOf("Arabisch A2"), wishes.fulfilled().first().map { it.title })
    }

    @Test
    fun `a part open again opens its goals again, up to the top, out of the review`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val a1 = wishes.create(WishDraft("A1 abschließen", parentId = arabic))
        val book = todos.create(TodoDraft("Lehrbuch", goalId = a1))
        todos.markDone(book)
        assertEquals(WishStatus.Done, wishes.get(arabic)?.status)

        todos.reopen(book)

        assertEquals(WishStatus.InProgress, wishes.get(a1)?.status)
        assertEquals(WishStatus.InProgress, wishes.get(arabic)?.status)
        assertNull(wishes.get(arabic)?.doneOn)
        assertEquals(emptyList<Wish>(), wishes.fulfilled().first())
    }

    @Test
    fun `a to-do ticked off by its checklist, or a subgoal set done, counts for its goal too`() = runBlocking {
        val move = wishes.create(WishDraft("Umzug"))
        val packing = todos.create(TodoDraft("Kisten packen", checklist = listOf(ChecklistDraftItem(text = "Bücher")), goalId = move))
        val flat = wishes.create(WishDraft("Wohnung finden", parentId = move))

        todos.tick(packing, todos.checklist(packing).single().id, ticked = true)
        assertEquals(WishStatus.Open, wishes.get(move)?.status)

        wishes.update(flat, WishDraft("Wohnung finden", parentId = move, status = WishStatus.Done))
        assertEquals(WishStatus.Done, wishes.get(move)?.status)
    }

    @Test
    fun `an open part added to a goal done opens it again, and a part moved away settles both goals`() = runBlocking {
        val move = wishes.create(WishDraft("Umzug"))
        val trip = wishes.create(WishDraft("Reise"))
        val packing = todos.create(TodoDraft("Kisten packen", goalId = move))
        todos.markDone(packing)
        assertEquals(WishStatus.Done, wishes.get(move)?.status)

        val boxes = todos.create(TodoDraft("Kartons kaufen", goalId = move))
        assertEquals(WishStatus.InProgress, wishes.get(move)?.status)

        todos.update(boxes, TodoDraft("Kartons kaufen", goalId = trip))
        assertEquals(WishStatus.Done, wishes.get(move)?.status)
        assertEquals(WishStatus.Open, wishes.get(trip)?.status)
    }

    @Test
    fun `a goal set done by hand stays so until one of its parts changes`() = runBlocking {
        val move = wishes.create(WishDraft("Umzug"))
        todos.create(TodoDraft("Kisten packen", goalId = move))

        wishes.update(move, WishDraft("Umzug", status = WishStatus.Done))

        assertEquals(WishStatus.Done, wishes.get(move)?.status)
    }

    @Test
    fun `deleting a goal deletes its subgoals, and its to-dos stay, hanging at no goal`() = runBlocking {
        val arabic = wishes.create(WishDraft("Arabisch A2"))
        val a1 = wishes.create(WishDraft("A1 abschließen", parentId = arabic))
        val book = todos.create(TodoDraft("Lehrbuch", goalId = a1))

        wishes.delete(arabic)

        assertNull(wishes.get(a1))
        assertNull(todos.get(book)?.goalId)
        assertEquals("Lehrbuch", todos.get(book)?.title)
    }

    @Test
    fun `deleting a part settles its goal`() = runBlocking {
        val move = wishes.create(WishDraft("Umzug"))
        val packing = todos.create(TodoDraft("Kisten packen", goalId = move))
        val boxes = todos.create(TodoDraft("Kartons kaufen", goalId = move))
        todos.markDone(packing)

        todos.delete(boxes)

        assertEquals(WishStatus.Done, wishes.get(move)?.status)
    }

    @Test
    fun `a goal deleted meanwhile is left out on saving`() = runBlocking {
        val gone = wishes.create(WishDraft("Weg"))
        wishes.delete(gone)

        val wish = wishes.create(WishDraft("Bergtour", parentId = gone))
        val todo = todos.create(TodoDraft("Schuhe kaufen", goalId = gone))

        assertNull(wishes.get(wish)?.parentId)
        assertNull(todos.get(todo)?.goalId)
    }

    @Test
    fun `progress set by hand stays until reset, then it is worked out from the parts again`() = runBlocking {
        val move = wishes.create(WishDraft("Umzug"))
        todos.create(TodoDraft("Kisten packen", goalId = move))

        wishes.update(move, WishDraft("Umzug", manualProgress = 70))
        assertEquals(70, wishes.current().first().single().percent)

        wishes.update(move, WishDraft("Umzug", manualProgress = null))
        assertEquals(0, wishes.current().first().single().percent)
    }

    @Test
    fun `progress set by hand is a whole percent from 0 to 100`() {
        assertEquals(setOf(WishProblem.ProgressOutOfRange), WishDraft("Umzug", manualProgress = 101).problems)
        assertEquals(setOf(WishProblem.ProgressOutOfRange), WishDraft("Umzug", manualProgress = -1).problems)
        assertEquals(emptySet<WishProblem>(), WishDraft("Umzug", manualProgress = 100).problems)
    }

    @Test
    fun `a wish made into a to-do gives one with its title, description and tags, hanging at it`() = runBlocking {
        val taxes = tags.create("Steuern")
        val wish = wishes.create(WishDraft("Steuererklärung", description = "Belege sammeln", tagIds = setOf(taxes)))

        val todo = todos.create(wishes.asTodo(wish))

        val saved = checkNotNull(todos.get(todo))
        assertEquals("Steuererklärung" to "Belege sammeln", saved.title to saved.description)
        assertEquals(wish, saved.goalId)
        assertEquals(setOf(taxes), todos.tagIdsOf(todo))
        assertEquals(listOf("Steuererklärung"), wishes.current().first().single().todos.map { it.title })
    }
}
