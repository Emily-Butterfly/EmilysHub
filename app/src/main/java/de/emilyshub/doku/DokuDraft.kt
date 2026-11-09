package de.emilyshub.doku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What an open page of the Doku has not saved yet, as the domain module it belongs to judges it: written before the Doku
 * locks ([pending], as any [DokuSaver]) and as the page closes ([close]). The page builds it anew from what it shows, by
 * [Assessments.draft], [Appointments.draft], [Appointments.templateDraft] or [Patients.textDraft], and hands it to the one
 * Composable that keeps it; what to write, keep or drop is decided here, not in the page.
 */
interface DokuDraft : DokuSaver {
    /**
     * Writes on the background, as the page goes away, what is to be written; how it goes is [leaving]. Nothing once the
     * Doku is locked.
     */
    fun close(leaving: Leaving)

    companion object {
        /** Nothing to write. */
        val None: DokuDraft = object : DokuDraft {
            override fun pending(): (suspend (DokuDatabase) -> Unit)? = null
            override fun close(leaving: Leaving) = Unit
        }
    }
}

/** How a page of the Doku goes away: for good; only as the screen turns, to go on with what it has; or under one opened over it. */
enum class Leaving { Closed, Turning, Covered }

/**
 * The [current] text of an editor opened with [original]: once changed, written by [write] before the Doku locks and as
 * the page closes, also as the screen turns, as the page keeps it nowhere else.
 */
internal class TextDraft(
    private val doku: Doku,
    private val background: CoroutineScope,
    private val original: String,
    private val current: String,
    private val write: suspend (DokuDatabase, String) -> Unit,
) : DokuDraft {
    private val changed: Boolean get() = current != original

    override fun pending(): (suspend (DokuDatabase) -> Unit)? = if (changed) ({ write(it, current) }) else null

    override fun close(leaving: Leaving) {
        if (changed) background.launch { doku.changing { write(it, current) } }
    }
}
