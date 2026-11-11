package de.emilyshub.ui.navigation

import de.emilyshub.search.SearchArea
import de.emilyshub.ui.ledger.BookingEditorTarget
import de.emilyshub.ui.ledger.RecurringEditorTarget
import de.emilyshub.ui.ledger.SubscriptionEditorTarget
import de.emilyshub.ui.notes.NoteEditorTarget
import de.emilyshub.ui.places.PlaceEditorTarget
import de.emilyshub.ui.routines.RoutineEditorTarget
import de.emilyshub.ui.todos.TodoEditorTarget
import de.emilyshub.ui.wishes.WishEditorTarget
import java.io.Serializable
import java.time.Year
import java.time.YearMonth

/**
 * A screen over the tabs (SPEC.md §1), with what it shows ([BackStack]). Kept as Android keeps a screen's state, so
 * that it outlives the app's process; its [stateKey] tells under what it keeps what was typed and scrolled there
 * while covered.
 */
sealed interface Destination : Serializable {

    /**
     * Under what the screen keeps its state: its kind, by the name its data class gives it, which shrinking the app
     * leaves as it is, so that the same screen shows another month or query with it; per target for an [Editor].
     */
    val stateKey: String get() = toString().substringBefore('(')

    /** An editor, of what its [target] is: one of something else starts afresh. */
    sealed interface Editor : Destination {
        val target: Serializable

        override val stateKey get() = "${toString().substringBefore('(')} $target"
    }

    /** The settings (SPEC.md §1). */
    data object Settings : Destination

    /** "Funktioniert alles?" (SPEC.md §11). */
    data object Status : Destination

    /** The places (SPEC.md §4). */
    data object Places : Destination

    /** A place's editor (SPEC.md §4). */
    data class PlaceEditor(override val target: PlaceEditorTarget) : Editor

    /** The tags (SPEC.md §8). */
    data object Tags : Destination

    /** The backups (SPEC.md §10). */
    data object Backups : Destination

    /** A routine's editor (SPEC.md §2). */
    data class RoutineEditor(override val target: RoutineEditorTarget) : Editor

    /** The routines archived (SPEC.md §2). */
    data object RoutineArchive : Destination

    /** The statistics of all routines (SPEC.md §8). */
    data object Statistics : Destination

    /** A routine's statistics (SPEC.md §8). */
    data class RoutineStatistics(val routineId: Long) : Destination

    /** The search, with what was typed, kept while an entry found is open (SPEC.md §7). */
    data class Search(val query: String = "") : Destination

    /** A to-do's editor (SPEC.md §5). */
    data class TodoEditor(override val target: TodoEditorTarget) : Editor

    /** The to-dos ticked off, in the archive (SPEC.md §5). */
    data object TodoArchive : Destination

    /** A goal's editor (SPEC.md §6). */
    data class WishEditor(override val target: WishEditorTarget) : Editor

    /** The goals come true, in the review (SPEC.md §6). */
    data object WishReview : Destination

    /** A note's editor (SPEC.md §7). */
    data class NoteEditor(override val target: NoteEditorTarget) : Editor

    /** The notes' tabs (SPEC.md §7). */
    data object NoteTabs : Destination

    /** The notes and folders archived (SPEC.md §7). */
    data object NoteArchive : Destination

    /** The household book, at a [month] (SPEC.md §13). */
    data class Ledger(val month: YearMonth) : Destination

    /** The household book's overview of a [year] (SPEC.md §13). */
    data class LedgerYear(val year: Year) : Destination

    /** The household book's categories (SPEC.md §13). */
    data object LedgerCategories : Destination

    /**
     * The budget calculator (SPEC.md §13): the monthly budgets against the standard total budget, with [month] null, or
     * the budgets of the open [month] against its own.
     */
    data class BudgetCalculator(val month: YearMonth?) : Destination

    /** The months closed, in the archive (SPEC.md §13). */
    data object LedgerArchive : Destination

    /** The fixed bookings (SPEC.md §13). */
    data object Recurring : Destination

    /** A fixed booking's editor (SPEC.md §13). */
    data class RecurringEditor(override val target: RecurringEditorTarget) : Editor

    /** A booking's editor (SPEC.md §13). */
    data class BookingEditor(override val target: BookingEditorTarget) : Editor

    /** The Abos (SPEC.md §13). */
    data object Subscriptions : Destination

    /** An Abo's editor (SPEC.md §13). */
    data class SubscriptionEditor(override val target: SubscriptionEditorTarget) : Editor

    companion object {
        /** The editor of an entry the search found (SPEC.md §7), in [area], by its [id]. */
        fun found(area: SearchArea, id: Long): Destination = when (area) {
            SearchArea.Routines -> RoutineEditor(RoutineEditorTarget.Edit(id))
            SearchArea.Todos -> TodoEditor(TodoEditorTarget.Edit(id))
            SearchArea.Wishes -> WishEditor(WishEditorTarget.Edit(id))
            SearchArea.Notes -> NoteEditor(NoteEditorTarget.Edit(id))
            SearchArea.Bookings -> BookingEditor(BookingEditorTarget.Edit(id))
            SearchArea.Subscriptions -> SubscriptionEditor(SubscriptionEditorTarget.Edit(id))
        }
    }
}
