package de.emilyshub.inbox

import androidx.room.withTransaction
import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.time.Clock
import kotlin.coroutines.CoroutineContext

/**
 * The Eingang (SPEC.md §16): thoughts captured with a line and nothing more, to be sorted in later as a to-do, a note
 * or a routine. [database] is only called on [io], since the first call opens it. Writes run to the end once started,
 * even if the screen that started them goes away.
 */
class Inbox(
    private val database: () -> HubDatabase,
    private val clock: Clock,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    private val dao get() = database().inbox()

    /** What is in it, the newest first. */
    fun entries(): Flow<List<InboxEntry>> = flow { emitAll(dao.observeAll()) }.flowOn(io)

    /** How many entries there are. */
    fun count(): Flow<Int> = flow { emitAll(dao.observeCount()) }.flowOn(io)

    /** The entry [id]; null once it is gone. */
    suspend fun entry(id: Long): InboxEntry? = withContext(io) { dao.find(id) }

    /** Puts [text] in, captured now; whether it could: nothing to see, or more than [InboxEntry.TEXT_MAX], is not. */
    suspend fun capture(text: String): Boolean {
        val cleaned = checked(text) ?: return false
        write { dao.insert(InboxEntry(text = cleaned, capturedAt = clock.instant())) }
        return true
    }

    /** Changes the text of the entry [id], as [capture] takes one; whether it could. */
    suspend fun change(id: Long, text: String): Boolean {
        val cleaned = checked(text) ?: return false
        write { dao.changeText(id, cleaned) }
        return true
    }

    /** Deletes the entry [id] and returns it as it was, for [restore]; null if it was gone already. */
    suspend fun delete(id: Long): InboxEntry? = write {
        database().withTransaction { dao.find(id)?.also { dao.delete(id) } }
    }

    /** Puts an entry [deleted] back as it was, under its id and when it was captured. */
    suspend fun restore(deleted: InboxEntry) = write {
        database().withTransaction { if (dao.find(deleted.id) == null) dao.insert(deleted) }
    }

    /** The entry [id] is sorted in, as what it became was saved: it leaves the inbox. Gone meanwhile, nothing happens. */
    suspend fun sortedOut(id: Long) = write { dao.delete(id) }

    private suspend fun <T> write(block: suspend () -> T): T = withContext(io + NonCancellable) { block() }

    companion object {
        /** [text] as it is kept: on its lines, without spaces at its edges; null if it cannot be kept. */
        fun checked(text: String): String? =
            InputText.multiLine(text).trim().takeIf { InputText.isVisible(it) && InputText.lengthOf(it) <= InboxEntry.TEXT_MAX }
    }
}

/**
 * An entry of the inbox ([entryId]) to sort in (SPEC.md §16), as the editor of what it becomes starts: its first line as
 * the [title], cut to what the title takes, and the [rest] below, starting with what did not fit into the title.
 */
data class Sorting(val entryId: Long, val title: String, val rest: String) : Serializable {
    companion object {
        /** [text] split into a title of at most [titleMax] characters and the rest. */
        fun of(entryId: Long, text: String, titleMax: Int): Sorting {
            val lines = text.trim().lines()
            val first = InputText.singleLine(lines.first())
            val (title, overflow) = cut(first, titleMax)
            val rest = (listOf(overflow) + lines.drop(1)).joinToString("\n").trim()
            return Sorting(entryId, title, rest)
        }

        /** [line] cut to [max] characters, at a space if there is one, and what is left over. */
        private fun cut(line: String, max: Int): Pair<String, String> {
            if (InputText.lengthOf(line) <= max) return line to ""
            val end = line.offsetByCodePoints(0, max)
            // Of a word cut in two, all goes to what is left over.
            val space = if (line[end] == ' ') end else line.lastIndexOf(' ', end - 1)
            val at = if (space > 0) space else end
            return line.substring(0, at).trimEnd() to line.substring(at).trimStart()
        }
    }
}
