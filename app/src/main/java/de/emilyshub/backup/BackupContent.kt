package de.emilyshub.backup

import de.emilyshub.doku.DokuSnapshot
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Base64

/** The [rows] of a table under its [columns], each value a Long, a Double, a String or null, as SQLite keeps them. */
class TableRows(val columns: List<String>, val rows: List<List<Any?>>)

/**
 * All data of the app as a backup holds it (SPEC.md §10): the rows of each of the [tables], from a database of the
 * [schema] version given, as they were when [created]; and the [doku] in its own encryption, if it has a password. As
 * JSON: {"schema": 16, "created": "2026-10-23T05:00:00Z", "tables": {"routines": {"columns": ["id", ...], "rows":
 * [[1, ...], ...]}, ...}, "doku": {"schema": 4, "keys": "<Base64>", "database": "<Base64>", "wal": "<Base64>"}}, the
 * Doku's write-ahead log only if it has one, the Doku only if there is one.
 */
class BackupContent(val schema: Int, val created: Instant, val tables: Map<String, TableRows>, val doku: DokuSnapshot? = null) {

    fun toJson(): ByteArray {
        val json = JSONObject()
        tables.forEach { (name, table) ->
            val rows = JSONArray()
            table.rows.forEach { row -> rows.put(JSONArray(row.map { it ?: JSONObject.NULL })) }
            json.put(name, JSONObject().put(COLUMNS, JSONArray(table.columns)).put(ROWS, rows))
        }
        val root = JSONObject().put(SCHEMA, schema).put(CREATED, created.toString()).put(TABLES, json)
        doku?.let { root.put(DOKU, dokuJson(it)) }
        return root.toString().toByteArray()
    }

    private fun dokuJson(doku: DokuSnapshot): JSONObject {
        val base64 = Base64.getEncoder()
        return JSONObject()
            .put(SCHEMA, doku.schema)
            .put(KEYS, base64.encodeToString(doku.sealedKeys))
            .put(DATABASE, base64.encodeToString(doku.database))
            .apply { doku.wal?.let { put(WAL, base64.encodeToString(it)) } }
    }

    companion object {
        private const val SCHEMA = "schema"
        private const val CREATED = "created"
        private const val TABLES = "tables"
        private const val COLUMNS = "columns"
        private const val ROWS = "rows"
        private const val DOKU = "doku"
        private const val KEYS = "keys"
        private const val DATABASE = "database"
        private const val WAL = "wal"

        /**
         * Read strictly: a key missing or unknown, or a value of another type, counts as damage, as does nesting deeper
         * than a backup has, which the parser would follow until its stack ran out.
         */
        fun fromJson(json: ByteArray): BackupContent = try {
            if (depthOf(json) > MAX_DEPTH) damaged()
            val root = JSONObject(String(json, Charsets.UTF_8))
            // Older backups, and those made without a Doku, have none.
            if (root.keySet() - DOKU != setOf(SCHEMA, CREATED, TABLES)) damaged()
            val tables = root.getJSONObject(TABLES)
            BackupContent(
                schema = root.get(SCHEMA) as? Int ?: damaged(),
                created = Instant.parse(root.get(CREATED) as? String ?: damaged()),
                tables = tables.keySet().associateWith { tableRows(tables.getJSONObject(it)) },
                doku = if (root.has(DOKU)) doku(root.getJSONObject(DOKU)) else null,
            )
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.Damaged, e)
        } catch (e: DateTimeParseException) {
            throw BackupException(BackupProblem.Damaged, e)
        } catch (e: IllegalArgumentException) {
            // Base64 that is none.
            throw BackupException(BackupProblem.Damaged, e)
        }

        /** Its keys must be sealed under the Doku password alone; the database is checked only once it is opened. */
        private fun doku(doku: JSONObject): DokuSnapshot {
            if (doku.keySet() - WAL != setOf(SCHEMA, KEYS, DATABASE)) damaged()
            val base64 = Base64.getDecoder()
            fun bytes(key: String) = base64.decode(doku.get(key) as? String ?: damaged())
            return DokuSnapshot.of(
                schema = doku.get(SCHEMA) as? Int ?: damaged(),
                sealedKeys = bytes(KEYS),
                database = bytes(DATABASE),
                wal = if (doku.has(WAL)) bytes(WAL) else null,
            ) ?: damaged()
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
