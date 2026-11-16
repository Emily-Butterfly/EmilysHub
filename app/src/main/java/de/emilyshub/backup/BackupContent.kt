package de.emilyshub.backup

import de.emilyshub.data.HubDatabase
import de.emilyshub.doku.DokuDatabase
import de.emilyshub.island.IslandFile
import de.emilyshub.island.IslandSnapshot
import de.emilyshub.tracking.TrackingDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/** The [rows] of a table under its [columns], each value a Long, a Double, a String or null, as SQLite keeps them. */
class TableRows(val columns: List<String>, val rows: List<List<Any?>>)

/** An attachment a backup carries after its content: kept under [file], [bytes] large. */
data class BackupAttachment(val file: String, val bytes: Long)

/**
 * All data of the app as a backup holds it (SPEC.md §10): the rows of each of the [tables], from a database of the
 * [schema] version given, as they were when [created]; the [doku] and [tracking] each in its own encryption, if it
 * has a password. As JSON: {"schema": 16, "created": "2026-10-23T05:00:00Z", "tables": {"routines": {"columns":
 * ["id", ...], "rows": [[1, ...], ...]}, ...}, "doku": {"schema": 4, "keys": "<Base64>", "database": 1234,
 * "wal": 56, "files": [["<name>", 789], ...]}}, the Doku's write-ahead log only if it has one, its own files only if it
 * has any, the Doku only if there is one, and "tracking" as "doku"; and, if there are any, the [attachments] that follow
 * the content in the file, in their order: "attachments": [["<file>", 1234], ...].
 *
 * The islands' database and log are not in the content but follow it as records of their own ([records]), each cut
 * into pieces of at most [PIECE_BYTES], their sizes in the content, then each of their own files, the photos of
 * Tracking's events (#133), in a record of its own: so that they do not share the content's [BackupFile.MAX_BYTES] with
 * all else, and are read one by one. Before format 3 of [BackupFile] the database and log were in the content, in
 * Base64; such backups still open.
 */
class BackupContent(
    val schema: Int,
    val created: Instant,
    val tables: Map<String, TableRows>,
    val doku: IslandSnapshot? = null,
    val tracking: IslandSnapshot? = null,
    val attachments: List<BackupAttachment> = emptyList(),
) {

    fun toJson(): ByteArray {
        val json = JSONObject()
        tables.forEach { (name, table) ->
            val rows = JSONArray()
            table.rows.forEach { row -> rows.put(JSONArray(row.map { it ?: JSONObject.NULL })) }
            json.put(name, JSONObject().put(COLUMNS, JSONArray(table.columns)).put(ROWS, rows))
        }
        val root = JSONObject().put(SCHEMA, schema).put(CREATED, created.toString()).put(TABLES, json)
        doku?.let { root.put(DOKU, islandJson(it)) }
        tracking?.let { root.put(TRACKING, islandJson(it)) }
        if (attachments.isNotEmpty()) {
            root.put(ATTACHMENTS, JSONArray(attachments.map { JSONArray(listOf(it.file, it.bytes)) }))
        }
        return root.toString().toByteArray()
    }

    private fun islandJson(island: IslandSnapshot): JSONObject = JSONObject()
        .put(SCHEMA, island.schema)
        .put(KEYS, Base64.getEncoder().encodeToString(island.sealedKeys))
        .put(DATABASE, island.database.size)
        .apply { island.wal?.let { put(WAL, it.size) } }
        .apply { if (island.files.isNotEmpty()) put(FILES, JSONArray(island.files.map { JSONArray(listOf(it.name, it.bytes)) })) }

    /**
     * The records that follow the content, before the [attachments]: the Doku's database and log, in pieces of at most
     * [PIECE_BYTES], and its own files, one each; then Tracking's. Made one by one as they are written.
     */
    fun records(): Sequence<ByteArray> = listOfNotNull(doku, tracking).asSequence().flatMap { island ->
        listOfNotNull(island.database, island.wal).asSequence().flatMap(::pieces) + island.files.asSequence().map { it.read() }
    }

    private fun pieces(bytes: ByteArray): Sequence<ByteArray> =
        (bytes.indices step PIECE_BYTES).asSequence().map { bytes.copyOfRange(it, minOf(it + PIECE_BYTES, bytes.size)) }

    companion object {
        private const val SCHEMA = "schema"
        private const val CREATED = "created"
        private const val TABLES = "tables"
        private const val COLUMNS = "columns"
        private const val ROWS = "rows"
        private const val DOKU = "doku"
        private const val TRACKING = "tracking"
        private const val KEYS = "keys"
        private const val DATABASE = "database"
        private const val WAL = "wal"
        private const val ATTACHMENTS = "attachments"
        private const val FILES = "files"

        /** The largest piece of an island's database or log, one record each. */
        const val PIECE_BYTES = 4 * 1024 * 1024

        /**
         * Read strictly: a key missing or unknown, or a value of another type, counts as damage, as does nesting deeper
         * than a backup has, which the parser would follow until its stack ran out. One of a newer version of the app
         * is told as such ([BackupProblem.NewerVersion]) before anything else is read; then the islands' databases and
         * logs are read from the [records] that follow the content, and their own files, each laid aside with [layAside],
         * given the island ("doku" or "tracking"), the file's name and its bytes: none if it is no name of such a file.
         */
        fun fromJson(
            json: ByteArray,
            records: () -> ByteArray? = { null },
            layAside: (island: String, name: String, bytes: ByteArray) -> IslandFile? = { _, _, _ -> null },
        ): BackupContent = try {
            if (depthOf(json) > MAX_DEPTH) damaged()
            val root = JSONObject(String(json, Charsets.UTF_8))
            // Older backups, and those made without a Doku, Tracking or attachments, have none.
            if (root.keySet() - DOKU - TRACKING - ATTACHMENTS != setOf(SCHEMA, CREATED, TABLES)) damaged()
            val schema = root.get(SCHEMA) as? Int ?: damaged()
            val doku = if (root.has(DOKU)) root.getJSONObject(DOKU) else null
            val tracking = if (root.has(TRACKING)) root.getJSONObject(TRACKING) else null
            val newer = schema > HubDatabase.VERSION ||
                (doku?.let(::islandSchema) ?: 0) > DokuDatabase.VERSION ||
                (tracking?.let(::islandSchema) ?: 0) > TrackingDatabase.VERSION
            if (newer) throw BackupException(BackupProblem.NewerVersion)
            val tables = root.getJSONObject(TABLES)
            BackupContent(
                schema = schema,
                created = Instant.parse(root.get(CREATED) as? String ?: damaged()),
                tables = tables.keySet().associateWith { tableRows(tables.getJSONObject(it)) },
                doku = doku?.let { island(it, records) { name, bytes -> layAside(DOKU, name, bytes) } },
                tracking = tracking?.let { island(it, records) { name, bytes -> layAside(TRACKING, name, bytes) } },
                attachments = if (root.has(ATTACHMENTS)) attachments(root.getJSONArray(ATTACHMENTS)) else emptyList(),
            )
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.Damaged, e)
        } catch (e: DateTimeParseException) {
            throw BackupException(BackupProblem.Damaged, e)
        } catch (e: IllegalArgumentException) {
            // Base64 that is none.
            throw BackupException(BackupProblem.Damaged, e)
        }

        /**
         * The Doku or Tracking: its keys must be sealed under its password alone; the database is checked only once it
         * is opened.
         */
        private fun island(island: JSONObject, records: () -> ByteArray?, layAside: (String, ByteArray) -> IslandFile?): IslandSnapshot {
            if (island.keySet() - WAL - FILES != setOf(SCHEMA, KEYS, DATABASE)) damaged()
            val base64 = Base64.getDecoder()
            fun bytes(key: String) = when (val value = island.get(key)) {
                // In the content, as before format 3.
                is String -> base64.decode(value)
                is Int -> carried(value.toLong(), records)
                is Long -> carried(value, records)
                else -> damaged()
            }
            val keys = base64.decode(island.get(KEYS) as? String ?: damaged())
            val database = bytes(DATABASE)
            val wal = if (island.has(WAL)) bytes(WAL) else null
            val files = if (island.has(FILES)) islandFiles(island.getJSONArray(FILES), records, layAside) else emptyList()
            return IslandSnapshot.of(islandSchema(island), keys, database, wal, files) ?: damaged()
        }

        /** The island's own files listed, each from the record that follows, as large as listed, laid aside. */
        private fun islandFiles(list: JSONArray, records: () -> ByteArray?, layAside: (String, ByteArray) -> IslandFile?): List<IslandFile> =
            List(list.length()) { index ->
                val entry = list.getJSONArray(index)
                if (entry.length() != 2) damaged()
                val name = entry.get(0) as? String ?: damaged()
                val size = when (val value = entry.get(1)) {
                    is Int -> value.toLong()
                    is Long -> value
                    else -> damaged()
                }
                val bytes = records() ?: damaged()
                if (bytes.size.toLong() != size) damaged()
                layAside(name, bytes) ?: damaged()
            }

        private fun islandSchema(island: JSONObject): Int = island.opt(SCHEMA) as? Int ?: damaged()

        /** [size] bytes, from the pieces that follow, each full but the last: no more than an island can have. */
        private fun carried(size: Long, records: () -> ByteArray?): ByteArray {
            if (size !in 0..IslandSnapshot.MAX_BYTES.toLong()) damaged()
            val bytes = ByteArray(size.toInt())
            var filled = 0
            while (filled < bytes.size) {
                val piece = records() ?: damaged()
                if (piece.size != minOf(PIECE_BYTES, bytes.size - filled)) damaged()
                piece.copyInto(bytes, filled)
                filled += piece.size
            }
            return bytes
        }

        private fun attachments(list: JSONArray): List<BackupAttachment> = List(list.length()) { index ->
            val entry = list.getJSONArray(index)
            if (entry.length() != 2) damaged()
            val bytes = when (val value = entry.get(1)) {
                is Int -> value.toLong()
                is Long -> value
                else -> damaged()
            }
            BackupAttachment(entry.get(0) as? String ?: damaged(), bytes)
        }

        private fun tableRows(table: JSONObject): TableRows {
            if (table.keySet() != setOf(COLUMNS, ROWS)) damaged()
            val columns = table.getJSONArray(COLUMNS)
            val rows = table.getJSONArray(ROWS)
            return TableRows(
                columns = List(columns.length()) { columns.get(it) as? String ?: damaged() },
                rows = List(rows.length()) { index ->
                    val row = rows.getJSONArray(index)
                    List(row.length()) { value(row.get(it)) }
                },
            )
        }

        private fun value(json: Any): Any? = when (json) {
            JSONObject.NULL -> null
            is Int -> json.toLong()
            is Long, is Double, is String -> json
            else -> damaged()
        }
        private fun JSONObject.keySet(): Set<String> = keys().asSequence().toSet()

        /** The root, the tables, a table, its rows and a row: no backup nests deeper. */
        private const val MAX_DEPTH = 5

        /** How deep objects and arrays nest in [json], brackets within strings aside; as deep as it goes before closing. */
        private fun depthOf(json: ByteArray): Int {
            var depth = 0
            var deepest = 0
            var inString = false
            var escaped = false
            for (byte in json) {
                val c = byte.toInt().toChar()
                when {
                    escaped -> escaped = false
                    inString && c == '\\' -> escaped = true
                    c == '"' -> inString = !inString
                    inString -> Unit
                    c == '{' || c == '[' -> deepest = maxOf(deepest, ++depth)
                    c == '}' || c == ']' -> depth--
                }
            }
            return deepest
        }
    }
}
