package de.emilyshub.wishes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Goals (SPEC.md §6): wishes with subgoals and to-dos to any depth, their progress worked out from those, and no cycles. */
class GoalTreeTest {

    @Test
    fun `the tree hangs each goal under its parent, to any depth, subgoals by title, then to-dos by title`() {
        val tree = build(
            listOf(
                wish(1, "Arabisch A2"),
                wish(2, "Wortschatz", parent = 1),
                wish(3, "A1 abschließen", parent = 1),
                wish(4, "Verben", parent = 2),
            ),
            listOf(todo(10, "A2-Prüfung anmelden", goal = 1), todo(11, "Anki einrichten", goal = 1)),
        )

        val arabic = tree.single()
        assertEquals(listOf("A1 abschließen", "Wortschatz"), arabic.subgoals.map { it.wish.title })
        assertEquals(listOf("A2-Prüfung anmelden", "Anki einrichten"), arabic.todos.map { it.title })
        assertEquals(listOf("Verben"), arabic.subgoals[1].subgoals.map { it.wish.title })
    }

    @Test
    fun `a goal's progress is the mean of its parts, worked out the same way all the way down`() {
        // The example of SPEC.md §6: A1 done by its to-dos, the vocabulary set to 60 % by hand, the exam not yet registered.
        // Its 40 % at the top only shows the idea; by the mean of the three parts it comes to 53 %.
        val tree = build(
            listOf(
                wish(1, "Arabisch A2"),
                wish(2, "A1 abschließen", parent = 1),
                wish(3, "Wortschatz 1000 Wörter", parent = 1, manualProgress = 60),
            ),
            listOf(
                todo(10, "Lehrbuch Kapitel 1–6", goal = 2, done = true),
                todo(11, "A1-Probetest machen", goal = 2, done = true),
                todo(12, "A2-Prüfung anmelden", goal = 1),
            ),
        )

        val arabic = tree.single()
        assertEquals(53, arabic.percent)
        assertEquals(listOf(100, 60), arabic.subgoals.map { it.percent })
    }

    @Test
    fun `a to-do not done counts by how much of its checklist is ticked off`() {
        val packing = todo(10, "Kisten packen", goal = 1, items = 4, ticked = 1)
        val goal = build(listOf(wish(1, "Umzug")), listOf(packing)).single()

        assertEquals(25, goal.percent)
    }

    @Test
    fun `a goal done is all done, one set by hand is as set, and without parts it is nothing until done`() {
        val tree = build(
            listOf(
                wish(1, "Bergtour", status = WishStatus.Done),
                wish(2, "Marathon", manualProgress = 30),
                wish(3, "Nordlichter sehen"),
                wish(4, "Umzug", status = WishStatus.Done),
            ),
            listOf(todo(10, "Kisten packen", goal = 4)),
        )

        assertEquals(
            mapOf("Bergtour" to 100, "Marathon" to 30, "Nordlichter sehen" to 0, "Umzug" to 100),
            tree.associate { it.wish.title to it.percent },
        )
        assertEquals(0, tree.single { it.wish.id == 4L }.automaticPercent)
    }

    @Test
    fun `progress shows 100 only when all is done and 0 only when nothing is`() {
        val todos = (1..200L).map { todo(it, "Schritt $it", goal = 1, done = it > 1) }
        val almost = build(listOf(wish(1, "Fast")), todos).single()
        val barely = build(listOf(wish(1, "Kaum")), todos.map { it.copy(done = it.id == 1L) }).single()

        assertEquals(99, almost.percent)
        assertEquals(1, barely.percent)
    }

    @Test
    fun `a goal cannot hang under itself, nor under any goal below it`() {
        // 1 ─ 2 ─ 3, and 4 apart.
        val parents = mapOf(1L to null, 2L to 1L, 3L to 2L, 4L to null)

        assertFalse(GoalTree.canHangUnder(goal = 1, parent = 1, parents::get))
        assertFalse(GoalTree.canHangUnder(goal = 1, parent = 2, parents::get))
        assertFalse(GoalTree.canHangUnder(goal = 1, parent = 3, parents::get))
        assertTrue(GoalTree.canHangUnder(goal = 3, parent = 1, parents::get))
        assertTrue(GoalTree.canHangUnder(goal = 1, parent = 4, parents::get))
        assertTrue(GoalTree.canHangUnder(goal = 2, parent = 4, parents::get))
    }

    @Test
    fun `goals caught in a cycle, which the app never makes, still show once each instead of hanging the app`() {
        val tree = build(listOf(wish(1, "A", parent = 2), wish(2, "B", parent = 1), wish(3, "C")), emptyList())

        assertEquals(setOf(1L, 2L, 3L), tree.flatMap { it.withAllBelow() }.map { it.wish.id }.toSet())
        assertEquals(3, tree.sumOf { it.withAllBelow().size })
    }

    private fun wish(
        id: Long,
        title: String,
        parent: Long? = null,
        status: WishStatus = WishStatus.Open,
        manualProgress: Int? = null,
    ) = WishWithTags(Wish(id, title, "", "", status, parentId = parent, manualProgress = manualProgress), emptyList())

    private fun todo(id: Long, title: String, goal: Long, done: Boolean = false, items: Int = 0, ticked: Int = 0) =
        GoalTodo(id, title, goal, done, items, ticked)

    /** The tree of [wishes] and [todos], without routines. */
    private fun build(wishes: List<WishWithTags>, todos: List<GoalTodo>) = GoalTree.build(wishes, todos, routines = emptyList())

    private fun Goal.withAllBelow(): List<Goal> = listOf(this) + subgoals.flatMap { it.withAllBelow() }
}
