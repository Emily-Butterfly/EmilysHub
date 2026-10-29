package de.emilyshub.search

import de.emilyshub.data.HubDatabase
import de.emilyshub.data.InputText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.CoroutineContext

/** The areas the search looks through, in the order their results show in. */
enum class SearchArea { Routines, Todos, Wishes, Notes }

/** An entry found, by its [title]: [id] opens it in its area. */
data class SearchHit(val id: Long, val title: String)

/** What was found in one [area], by title. */
data class SearchGroup(val area: SearchArea, val hits: List<SearchHit>)

/**
 * The search over all areas (SPEC.md §8): routines, to-dos, wishes and notes, by their titles, descriptions, the text of
 * notes and their tags. Every word typed has to be there, in whatever case. The database looks for the longest word,
 * as a value of the query, its wildcards escaped; as it tells case apart in letters such as "Ü", those stand for any
 * letter there, and what it finds is then looked at closely here. [database] is only called on [io].
 */
class Search(
    private val database: () -> HubDatabase,
    private val io: CoroutineContext = Dispatchers.IO,
) {
    /** What [typed] finds, area by area, those with nothing left out; nothing for nothing typed. */
    suspend fun find(typed: String): List<SearchGroup> = withContext(io) {
        val words = foldedCase(InputText.singleLine(typed).take(MAX_LENGTH)).split(' ').filter(String::isNotEmpty)
        if (words.isEmpty()) return@withContext emptyList()
        val pattern = patternFor(words.maxBy { it.length })
        val dao = database().search()
        val found = mapOf(
            SearchArea.Routines to dao.routines(pattern),
            SearchArea.Todos to dao.todos(pattern),
            SearchArea.Wishes to dao.wishes(pattern),
            SearchArea.Notes to dao.notes(pattern),
        )
        found.mapNotNull { (area, candidates) ->
            val hits = candidates.filter { candidate -> foldedCase(candidate.text).let { text -> words.all { it in text } } }
                .map { SearchHit(it.id, it.title) }
                .sortedWith(compareBy(InputText.byName) { it.title })
                .take(MAX_HITS)
            if (hits.isEmpty()) null else SearchGroup(area, hits)
        }
    }

    private companion object {
        /** As long as a search term need be. */
        const val MAX_LENGTH = 100

        /** As many per area as make sense to scroll through. */
        const val MAX_HITS = 50

        fun foldedCase(text: String) = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase(Locale.GERMAN)

        /**
         * "%word%", with the wildcards and the escape character in [word] escaped, and letters beyond ASCII, which the
         * database compares in their case, standing for any one letter.
         */
        fun patternFor(word: String): String = buildString {
            append('%')
            word.forEach { c ->
                when {
                    c == '%' || c == '_' || c == '\\' -> append('\\').append(c)
                    c.code > ASCII_END -> append('_')
                    else -> append(c)
                }
            }
            append('%')
        }

        const val ASCII_END = 127
    }
}
