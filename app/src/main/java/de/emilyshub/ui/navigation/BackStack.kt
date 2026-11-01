package de.emilyshub.ui.navigation

import java.io.Serializable
import java.time.Year
import java.time.YearMonth

/**
 * The screens open over the tabs (SPEC.md §1), the one shown last ([top]): each opened from the one below it, which
 * back goes to ([pop]); none open, the tabs show. A value, so that how one goes from screen to screen can be tested
 * without the UI, and kept as Android keeps a screen's state.
 */
data class BackStack(val entries: List<Destination> = emptyList()) : Serializable {

    /** The screen shown; none while the tabs show. */
    val top: Destination? get() = entries.lastOrNull()

    /**
     * Under what each screen open keeps what was typed and scrolled there, by its place: two of a kind, as the
     * household book at two months, each keep their own; one of a kind put in the place of another of it
     * ([replaceTop]) keeps it, unless it is an editor of something else ([Destination.stateKey]).
     */
    val stateKeys: List<String> get() = entries.mapIndexed { place, destination -> "$place ${destination.stateKey}" }

    /** Under what the screen shown keeps its state, as [stateKeys] has it; none while the tabs show. */
    val topStateKey: String? get() = stateKeys.lastOrNull()

    /** [destination] opened on top. */
    fun push(destination: Destination) = BackStack(entries + destination)

    /** Back to the screen below; the tabs once none is. */
    fun pop() = BackStack(entries.dropLast(1))

    /** [destination] in the place of the one shown, going back where that one would; on top if none is. */
    fun replaceTop(destination: Destination) = pop().push(destination)

    /** The search shown, searching for [query] as typed (SPEC.md §7); nothing while another screen shows. */
    fun search(query: String) = if (top is Destination.Search) replaceTop(Destination.Search(query)) else this

    /**
     * The overview of the year of the month shown in the household book (SPEC.md §13). From a month opened from an
     * overview, it is that overview again, at this month's year, going back where it would; from any other month, it
     * goes back to it. Nothing while the household book does not show.
     */
    fun openLedgerYear(): BackStack {
        val month = (top as? Destination.Ledger)?.month ?: return this
        val year = Destination.LedgerYear(Year.from(month))
        return if (pop().top is Destination.LedgerYear) pop().replaceTop(year) else push(year)
    }

    /** The [month] chosen in the archive, shown in the household book it was opened from (SPEC.md §13). */
    fun openArchivedMonth(month: YearMonth): BackStack = pop().replaceTop(Destination.Ledger(month))

    private companion object {
        /** Kept stacks read back as long as what is on them is the same. */
        private const val serialVersionUID = 1L
    }
}
