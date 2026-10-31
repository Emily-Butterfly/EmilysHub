package de.emilyshub.search

import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/** The areas the search looks through, in the order their results show in. */
enum class SearchArea { Routines, Todos, Wishes, Notes }

/** An entry found, by its [title]: [id] opens it in its area. */
data class SearchHit(val id: Long, val title: String)

/** What was found in one [area], by title, and how many [more] there are beyond those shown. */
data class SearchGroup(val area: SearchArea, val hits: List<SearchHit>, val more: Int = 0)

/**
 * The search over all areas (SPEC.md §8): routines, to-dos, wishes and notes, by their titles, descriptions, the text of
 * notes and their tags. Every word typed has to be there, in whatever case, and however its letters were typed, as
 * [InputText.readTheSame] compares. The database looks for the longest word, as a value of the query, its wildcards
 * escaped; as it tells case apart in letters such as "Ü", and may hold them in two parts, those stand for anything
 * there, and what it finds is then looked at closely here. [database] is only called on [io].
 */
class Search(
    private val database: () -> HubDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    /** What [typed] finds, area by area, those with nothing left out; nothing for nothing typed. */
    suspend fun find(typed: String): List<SearchGroup> = withContext(io) {
        val words = InputText.comparable(InputText.singleLine(typed).take(MAX_LENGTH)).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return@withContext emptyList()
        // "ß" beyond ASCII stands for anything, so that an "ss" typed finds one.
        val pattern = patternFor(words.maxBy { it.length }.replace("ss", "ß"))
        val dao = database().search()
        val found = mapOf(
            SearchArea.Routines to dao.routines(pattern),
            SearchArea.Todos to dao.todos(pattern),
            SearchArea.Wishes to dao.wishes(pattern),
            SearchArea.Notes to dao.notes(pattern),
        )
        found.mapNotNull { (area, candidates) ->
            val hits = candidates.filter { candidate -> InputText.comparable(candidate.text).let { text -> words.all { it in text } } }
                .map { SearchHit(it.id, it.title) }
                .sortedWith(compareBy(InputText.byName) { it.title })
            if (hits.isEmpty()) null else SearchGroup(area, hits.take(MAX_HITS), more = maxOf(hits.size - MAX_HITS, 0))
        }
    }

    private companion object {
        const val ASCII_END = 127

        /** As long as a search term need be. */
        const val MAX_LENGTH = 100

        /** As many per area as make sense to scroll through. */
        const val MAX_HITS = 50

        /**
         * "%word%", with the wildcards and the escape character in [word] escaped, and letters beyond ASCII, which the
         * database compares in their case and may hold in two parts, standing for anything, as does "ss", which may be
         * an "ß" there.
         */
        fun patternFor(word: String): String = buildString {
            // Whether what was put last stands for anything, not for a "%" typed.
            var anything = true
            append('%')
            word.forEach { c ->
                when {
                    c == '%' || c == '_' || c == '\\' -> append('\\').append(c)
                    c.code > ASCII_END -> if (!anything) append('%')
                    else -> append(c)
                }
                anything = c.code > ASCII_END
            }
            if (!anything) append('%')
        }
    }
}
