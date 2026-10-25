package de.emilyshub.backup

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import de.emilyshub.data.HubDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

/**
 * Brings the data of a backup made by an older version of the app up to this one (SPEC.md §10), as an update of the app
 * brings its database: in a database in memory, laid out as the app's was in that version by the schema Room exported
 * for it (in the assets), the data go in, checked as strictly as ever, and then the app's own migrations run on them.
 */
class BackupMigration(private val context: Context) {

    /** [content] of an older version as it would be in this one; fails as [BackupProblem.OlderVersion] if it cannot be. */
    fun upToDate(content: BackupContent): BackupContent {
        val migrations = migrationsFrom(content.schema)
        val layout = layoutOf(content.schema)
        inMemory(content.schema).use { helper ->
            val db = helper.writableDatabase
            layout.forEach(db::execSQL)
            // In one transaction, as Room runs the migrations: they may count on it.
            db.beginTransaction()
            try {
                DataTables(db).replaceWith(content.tables)
                migrations.forEach { it.migrate(db) }
                return BackupContent(HubDatabase.VERSION, content.created, DataTables(db).read())
            } finally {
                db.endTransaction()
            }
        }
    }

    /** The app's migrations from [version] on, one after the other up to this one. */
    private fun migrationsFrom(version: Int): List<Migration> = buildList {
        var reached = version
        while (reached < HubDatabase.VERSION) {
            val next = HubDatabase.MIGRATIONS.find { it.startVersion == reached } ?: throw BackupException(BackupProblem.OlderVersion)
            add(next)
            reached = next.endVersion
        }
    }

    /** The statements that lay out the tables and indices of the database of [version], as Room exported its schema. */
    private fun layoutOf(version: Int): List<String> {
        val schema = try {
            context.assets.open("$SCHEMAS/$version.json").use { String(it.readBytes()) }
        } catch (e: FileNotFoundException) {
            throw BackupException(BackupProblem.OlderVersion, e)
        }
        val entities = JSONObject(schema).getJSONObject("database").getJSONArray("entities")
        return entities.objects().flatMap { entity ->
            val table = entity.getString("tableName")
            val indices = entity.optJSONArray("indices")?.objects().orEmpty().map { index ->
                index.getString("createSql").replace(INDEX_NAME, index.getString("name")).replace(TABLE_NAME, table)
            }
            listOf(entity.getString("createSql").replace(TABLE_NAME, table)) + indices
        }
    }

    /** Only in memory, so that nothing of the backup reaches the disk unencrypted. */
    private fun inMemory(version: Int): SupportSQLiteOpenHelper = FrameworkSQLiteOpenHelperFactory().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA temp_store = MEMORY")
                    }

                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build(),
    )

    private fun JSONArray.objects(): List<JSONObject> = List(length(), ::getJSONObject)

    private companion object {
        /** Where Room exports the schemas to, named after the database class, which R8 may rename in the app. */
        const val SCHEMAS = "de.emilyshub.data.HubDatabase"
        const val TABLE_NAME = "\${TABLE_NAME}"
        const val INDEX_NAME = "\${INDEX_NAME}"
    }
}
