package de.emilyshub.ui.navigation

import de.emilyshub.ui.ledger.BookingEditorTarget
import de.emilyshub.ui.ledger.RecurringEditorTarget
import de.emilyshub.ui.notes.NoteEditorTarget
import de.emilyshub.ui.places.PlaceEditorTarget
import de.emilyshub.ui.routines.RoutineEditorTarget
import de.emilyshub.ui.todos.TodoEditorTarget
import de.emilyshub.ui.wishes.WishEditorTarget
import java.io.Serializable
import java.time.Year
import java.time.YearMonth

/**
 * A screen over the tabs, with what it shows ([BackStack]). Kept as Android keeps a screen's state, so that it outlives
 * the app's process; its [stateKey] tells under what it keeps what was typed and scrolled there while covered.
 */
sealed interface Destination : Serializable {

    /** Under what the screen keeps its state: one per kind, as it shows another month or query; per target for editors. */
    val stateKey: String get() = javaClass.simpleName

    /** The settings (SPEC.md §1). */
    data object Settings : Destination

    /** "Funktioniert alles?" (SPEC.md §11). */
    data object Status : Destination

    /** The places (SPEC.md §4). */
    data object Places : Destination

    /** A place's editor. */
    data class PlaceEditor(val target: PlaceEditorTarget) : Destination {
        override val stateKey get() = "PlaceEditor $target"
    }

    /** The tags (SPEC.md §8). */
    data object Tags : Destination

    /** The backups (SPEC.md §10). */
    data object Backups : Destination

    /** A routine's editor (SPEC.md §2). */
    data class RoutineEditor(val target: RoutineEditorTarget) : Destination {
        override val stateKey get() = "RoutineEditor $target"
    }

    /** The routines archived (SPEC.md §2). */
    data object RoutineArchive : Destination

    /** The statistics of all routines (SPEC.md §8). */
    data object Statistics : Destination

    /** A routine's statistics. */
    data class RoutineStatistics(val routineId: Long) : Destination

    /** The search, with what was typed, kept while an entry found is open (SPEC.md §7). */
    data class Search(val query: String = "") : Destination

    /** A to-do's editor (SPEC.md §5). */
    data class TodoEditor(val target: TodoEditorTarget) : Destination {
        override val stateKey get() = "TodoEditor $target"
    }

    /** The to-dos ticked off. */
    data object TodoArchive : Destination

    /** A goal's editor (SPEC.md §6). */
    data class WishEditor(val target: WishEditorTarget) : Destination {
        override val stateKey get() = "WishEditor $target"
    }

    /** The goals come true. */
    data object WishReview : Destination

    /** A note's editor (SPEC.md §7). */
    data class NoteEditor(val target: NoteEditorTarget) : Destination {
        override val stateKey get() = "NoteEditor $target"
    }

    /** The notes' tabs. */
    data object NoteTabs : Destination

    /** The household book, at a [month] (SPEC.md §13). */
    data class Ledger(val month: YearMonth) : Destination

    /** The household book's overview of a [year]. */
    data class LedgerYear(val year: Year) : Destination

    /** The household book's categories. */
    data object LedgerCategories : Destination

    /** The months closed, in the archive. */
    data object LedgerArchive : Destination

    /** The fixed bookings. */
    data object Recurring : Destination

    /** A fixed booking's editor. */
    data class RecurringEditor(val target: RecurringEditorTarget) : Destination {
        override val stateKey get() = "RecurringEditor $target"
    }

    /** A booking's editor. */
    data class BookingEditor(val target: BookingEditorTarget) : Destination {
        override val stateKey get() = "BookingEditor $target"
    }
}
