package de.emilyshub.backup

import android.database.Cursor
import android.database.SQLException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement

/**
 * The tables of the app's data in [db]: all but the settings, which belong to the device, and those of SQLite and Room.
 * Their names and columns are read from the database itself, so that a backup takes in every table there is.
 */
internal class DataTables(private val db: SupportSQLiteDatabase) {

    private class Column(val name: String, val type: String, val notNull: Boolean)

    private val schema: Map<String, List<Column>> = names().associateWith(::columnsOf)

    fun read(): Map<String, TableRows> =
        schema.mapValues { (table, columns) -> TableRows(columns.map(Column::name), rowsOf(table)) }

    /**
     * Puts [tables] in place of all rows, once they are checked against the tables here: the same tables with the same
     * columns, each value of its column's type and there where one must be, no id twice and no reference to a row that
     * is not there. Fails with [BackupProblem.Damaged] otherwise; to be called in a transaction, which that rolls back.
     */
    fun replaceWith(tables: Map<String, TableRows>) {
        if (tables.keys != schema.keys) damaged()
        val checked = tables.mapValues { (table, rows) -> checked(rows, schema.getValue(table)) }
        try {
            // The rows go in table by table, so a reference may come before the row it refers to.
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            schema.keys.forEach { db.execSQL("DELETE FROM `$it`") }
            checked.forEach { (table, rows) -> insert(table, rows) }
        } catch (e: SQLException) {
            throw BackupException(BackupProblem.Damaged, e)
        }
        db.query("PRAGMA foreign_key_check").use { if (it.count > 0) damaged() }
    }

    private fun checked(table: TableRows, columns: List<Column>): TableRows {
        val byName = columns.associateBy(Column::name)
        if (table.columns.size != columns.size || table.columns.toSet() != byName.keys) damaged()
        val ordered = table.columns.map(byName::getValue)
        val rows = table.rows.map { row ->
            if (row.size != ordered.size) damaged()
            row.zip(ordered) { value, column -> checked(value, column) }
        }
        return TableRows(table.columns, rows)
    }

    private fun checked(value: Any?, column: Column): Any? = when {
        value == null -> if (column.notNull) damaged() else null
        column.type == INTEGER -> value as? Long ?: damaged()
        // JSON writes a whole number without its ".0".
        column.type == REAL -> (value as? Long)?.toDouble() ?: value as? Double ?: damaged()
        column.type == TEXT -> value as? String ?: damaged()
        else -> damaged()
    }

    private fun insert(table: String, rows: TableRows) {
        val columns = rows.columns.joinToString { "`$it`" }
        val values = rows.columns.joinToString { "?" }
        db.compileStatement("INSERT INTO `$table` ($columns) VALUES ($values)").use { statement ->
            rows.rows.forEach { row ->
                statement.clearBindings()
                row.forEachIndexed { index, value -> statement.bind(index + 1, value) }
                statement.executeInsert()
            }
        }
    }

    private fun SupportSQLiteStatement.bind(index: Int, value: Any?) = when (value) {
        null -> bindNull(index)
        is Long -> bindLong(index, value)
        is Double -> bindDouble(index, value)
        else -> bindString(index, value as String)
    }

    private fun names(): List<String> = db.query(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' " +
            "AND name NOT IN ('android_metadata', 'room_master_table', 'settings') ORDER BY name",
    ).use { cursor -> cursor.all { getString(0) } }

    private fun columnsOf(table: String): List<Column> =
        db.query("SELECT name, type, \"notnull\" FROM pragma_table_info(?) ORDER BY cid", arrayOf(table)).use { cursor ->
            cursor.all { Column(getString(0), getString(1), getInt(2) == 1) }
        }

    private fun rowsOf(table: String): List<List<Any?>> = db.query("SELECT * FROM `$table`").use { cursor ->
        cursor.all { List(columnCount) { valueAt(it) } }
    }

    private fun Cursor.valueAt(column: Int): Any? = when (getType(column)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(column)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(column)
        Cursor.FIELD_TYPE_STRING -> getString(column)
        else -> error("The app keeps no BLOBs, so backups take none")
    }

    private fun <T> Cursor.all(row: Cursor.() -> T): List<T> = buildList { while (moveToNext()) add(row()) }

    private companion object {
        const val INTEGER = "INTEGER"
        const val REAL = "REAL"
        const val TEXT = "TEXT"
    }
}
