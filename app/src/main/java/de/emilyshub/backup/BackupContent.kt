package de.emilyshub.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeParseException

/** The [rows] of a table under its [columns], each value a Long, a Double, a String or null, as SQLite keeps them. */
class TableRows(val columns: List<String>, val rows: List<List<Any?>>)

/**
 * All data of the app as a backup holds it (SPEC.md §10): the rows of each of the [tables], from a database of the
 * [schema] version given, as they were when [created]. As JSON: {"schema": 16, "created": "2026-10-23T05:00:00Z",
 * "tables": {"routines": {"columns": ["id", ...], "rows": [[1, ...], ...]}, ...}}.
 */
class BackupContent(val schema: Int, val created: Instant, val tables: Map<String, TableRows>) {

    fun toJson(): ByteArray {
        val json = JSONObject()
        tables.forEach { (name, table) ->
            val rows = JSONArray()
            table.rows.forEach { row -> rows.put(JSONArray(row.map { it ?: JSONObject.NULL })) }
            json.put(name, JSONObject().put(COLUMNS, JSONArray(table.columns)).put(ROWS, rows))
        }
        return JSONObject().put(SCHEMA, schema).put(CREATED, created.toString()).put(TABLES, json).toString().toByteArray()
    }

    companion object {
        private const val SCHEMA = "schema"
        private const val CREATED = "created"
        private const val TABLES = "tables"
        private const val COLUMNS = "columns"
        private const val ROWS = "rows"

        /** Read strictly: anything [toJson] would not have written counts as damage. */
        fun fromJson(json: ByteArray): BackupContent = try {
            val root = JSONObject(String(json, Charsets.UTF_8))
            val tables = root.getJSONObject(TABLES)
            BackupContent(
                schema = root.get(SCHEMA) as? Int ?: damaged(),
                created = Instant.parse(root.get(CREATED) as? String ?: damaged()),
                tables = tables.keys().asSequence().associateWith { tableRows(tables.getJSONObject(it)) },
            )
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.Damaged, e)
        } catch (e: DateTimeParseException) {
            throw BackupException(BackupProblem.Damaged, e)
        }

        private fun tableRows(table: JSONObject): TableRows {
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
    }
}

internal fun damaged(): Nothing = throw BackupException(BackupProblem.Damaged)
