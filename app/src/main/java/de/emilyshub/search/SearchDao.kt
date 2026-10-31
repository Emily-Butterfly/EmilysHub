package de.emilyshub.search

import androidx.room.Dao
import androidx.room.Query

/**
 * What the search looks through, in the database: each entry with all its text in one, its [Candidate.text], kept only
 * if that is LIKE the [pattern], escaped with a backslash. [Search] is the way to it.
 */
@Dao
interface SearchDao {
    @Query(
        "SELECT * FROM (SELECT r.id, r.name AS title, r.name || ' ' || r.description || ' ' || IFNULL((SELECT " +
            "group_concat(t.name, ' ') FROM routine_tags rt JOIN tags t ON t.id = rt.tag_id WHERE rt.routine_id = r.id), '') " +
            "AS text FROM routines r) WHERE text LIKE :pattern ESCAPE '\\'",
    )
    suspend fun routines(pattern: String): List<Candidate>

    /** Of a to-do coming again, the one now only. */
    @Query(
        "SELECT * FROM (SELECT t.id, t.title, t.title || ' ' || t.description || ' ' || IFNULL((SELECT " +
            "group_concat(g.name, ' ') FROM todo_tags tt JOIN tags g ON g.id = tt.tag_id WHERE tt.todo_id = t.id), '') " +
            "AS text FROM todos t WHERE t.next_id IS NULL) WHERE text LIKE :pattern ESCAPE '\\'",
    )
    suspend fun todos(pattern: String): List<Candidate>

    @Query(
        "SELECT * FROM (SELECT w.id, w.title, w.title || ' ' || w.description || ' ' || w.period || ' ' || IFNULL((SELECT " +
            "group_concat(g.name, ' ') FROM wish_tags wt JOIN tags g ON g.id = wt.tag_id WHERE wt.wish_id = w.id), '') " +
            "AS text FROM wishes w) WHERE text LIKE :pattern ESCAPE '\\'",
    )
    suspend fun wishes(pattern: String): List<Candidate>

    @Query(
        "SELECT * FROM (SELECT n.id, n.title, n.title || ' ' || n.text || ' ' || IFNULL((SELECT " +
            "group_concat(g.name, ' ') FROM note_tags nt JOIN tags g ON g.id = nt.tag_id WHERE nt.note_id = n.id), '') " +
            "AS text FROM notes n) WHERE text LIKE :pattern ESCAPE '\\'",
    )
    suspend fun notes(pattern: String): List<Candidate>

    /** By its description, or its category without one. */
    @Query(
        "SELECT * FROM (SELECT b.id, CASE WHEN b.description = '' THEN c.name ELSE b.description END AS title, " +
            "b.description || ' ' || c.name AS text FROM bookings b JOIN ledger_categories c ON c.id = b.category_id) " +
            "WHERE text LIKE :pattern ESCAPE '\\'",
    )
    suspend fun bookings(pattern: String): List<Candidate>
}

/** An entry the database found, with all its text in one, for [Search] to look at more closely. */
data class Candidate(val id: Long, val title: String, val text: String)
