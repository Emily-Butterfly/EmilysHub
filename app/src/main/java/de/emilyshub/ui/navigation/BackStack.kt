package de.emilyshub.ui.navigation

import java.io.Serializable
import java.time.Year
import java.time.YearMonth

/**
 * The screens open over the tabs, the one shown last ([top]): each opened from the one below it, which back goes to
 * ([pop]); none open, the tabs show. A value, so that how one goes from screen to screen can be tested without the UI,
 * and kept as Android keeps a screen's state.
 */
data class BackStack(val entries: List<Destination> = emptyList()) : Serializable {

    /** The screen shown; none while the tabs show. */
    val top: Destination? get() = entries.lastOrNull()

    /** [destination] opened on top. */
    fun push(destination: Destination) = BackStack(entries + destination)

    /** Back to the screen below; the tabs once none is. */
    fun pop() = BackStack(entries.dropLast(1))

    /** [destination] in the place of the one shown, going back where that one would; on top if none is. */
    fun replaceTop(destination: Destination) = pop().push(destination)

    /** The search shown searching for [query], as typed. */
    fun search(query: String) = replaceTop(Destination.Search(query))

    /**
     * The overview of the year of the month shown in the household book. From a month opened from an overview, it is
     * that overview again, at this month's year, going back where it would; from any other month, it goes back to it.
     */
    fun openLedgerYear(): BackStack {
        val month = (top as? Destination.Ledger)?.month ?: return this
        val year = Destination.LedgerYear(Year.from(month))
        return if (pop().top is Destination.LedgerYear) pop().replaceTop(year) else push(year)
    }

    /** The [month] chosen in the archive, shown in the household book it was opened from. */
    fun openArchivedMonth(month: YearMonth): BackStack = pop().replaceTop(Destination.Ledger(month))

    private companion object {
        private const val serialVersionUID = 1L
    }
}
