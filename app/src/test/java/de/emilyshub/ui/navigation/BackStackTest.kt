package de.emilyshub.ui.navigation

import de.emilyshub.ui.notes.NoteEditorTarget
import de.emilyshub.ui.todos.TodoEditorTarget
import de.emilyshub.ui.wishes.WishEditorTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.Year
import java.time.YearMonth

/**
 * Where the screens over the tabs come from and go back to: each opened on top of the one it was opened from, and back
 * goes to that one, as the rules of the household book's months and years and of the search have it.
 */
class BackStackTest {

    private val october = YearMonth.of(2026, 10)

    @Test
    fun `with nothing open, the tabs show, and back from a screen goes to the one it was opened from`() {
        val empty = BackStack()
        assertNull(empty.top)

        val stack = empty.push(Destination.Settings).push(Destination.Places)

        assertEquals(Destination.Places, stack.top)
        assertEquals(Destination.Settings, stack.pop().top)
        assertNull(stack.pop().pop().top)
        assertNull("back with nothing open stays so", empty.pop().top)
    }

    @Test
    fun `an entry found opens over the search, which keeps what was searched for until it is closed`() {
        val searching = BackStack().push(Destination.Search()).search("Arzt")

        val opened = searching.push(Destination.TodoEditor(TodoEditorTarget.Edit(7)))

        assertEquals(Destination.Search("Arzt"), opened.pop().top)
        assertNull(opened.pop().pop().top)
    }

    @Test
    fun `a note opened from an editor goes back to it`() {
        val stack = BackStack().push(Destination.TodoEditor(TodoEditorTarget.Edit(7))).push(Destination.NoteEditor(NoteEditorTarget.Edit(3)))

        assertEquals(Destination.TodoEditor(TodoEditorTarget.Edit(7)), stack.pop().top)
    }

    @Test
    fun `a to-do opened from a wish's editor takes its place, and goes back to where that was opened from`() {
        val stack = BackStack().push(Destination.WishReview).push(Destination.WishEditor(WishEditorTarget.Edit(4)))

        val todo = stack.replaceTop(Destination.TodoEditor(TodoEditorTarget.Edit(9)))

        assertEquals(Destination.TodoEditor(TodoEditorTarget.Edit(9)), todo.top)
        assertEquals(Destination.WishReview, todo.pop().top)
    }

    @Test
    fun `the year opened from a month goes back to it, and so does a month opened from that year`() {
        val month = BackStack().push(Destination.Ledger(october))

        val year = month.openLedgerYear()
        assertEquals(Destination.LedgerYear(Year.of(2026)), year.top)

        val march = year.push(Destination.Ledger(YearMonth.of(2026, 3)))
        assertEquals(Destination.LedgerYear(Year.of(2026)), march.pop().top)
        assertEquals(Destination.Ledger(october), march.pop().pop().top)
    }

    @Test
    fun `the year opened from a month opened from a year goes back where that year would`() {
        val march2025 = BackStack().push(Destination.Ledger(october)).openLedgerYear()
            .replaceTop(Destination.LedgerYear(Year.of(2025)))
            .push(Destination.Ledger(YearMonth.of(2025, 3)))

        val year = march2025.openLedgerYear()

        assertEquals(Destination.LedgerYear(Year.of(2025)), year.top)
        assertEquals(Destination.Ledger(october), year.pop().top)
    }

    @Test
    fun `a month chosen in the archive shows in the household book it was opened from`() {
        val stack = BackStack().push(Destination.Ledger(october)).push(Destination.LedgerArchive)

        val chosen = stack.openArchivedMonth(YearMonth.of(2025, 12))

        assertEquals(Destination.Ledger(YearMonth.of(2025, 12)), chosen.top)
        assertNull(chosen.pop().top)
    }

    @Test
    fun `it outlives the app's process, as all on it is kept the way Android keeps a screen's state`() {
        val stack = BackStack().push(Destination.Ledger(october)).openLedgerYear().push(Destination.Search("Miete"))

        val kept = ByteArrayOutputStream().also { ObjectOutputStream(it).writeObject(stack) }.toByteArray()

        assertEquals(stack, ObjectInputStream(ByteArrayInputStream(kept)).readObject())
    }
}
