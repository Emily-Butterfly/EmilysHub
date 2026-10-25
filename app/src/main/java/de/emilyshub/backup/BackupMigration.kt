package de.emilyshub.backup

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import de.emilyshub.data.HubDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

/**
 * Brings the data of a backup made by an older version of the app up to this one (SPEC.md §10), as an update of the app
 * brings its database: in a database in memory, laid out as the app's was in that version by the schema Room exported
 * for it (in the assets), the data go in, checked as strictly as ever, and then the app's own migrations run on them.
 * The database comes from [openHelpers], so that the migrations run on the same SQLite as in an update.
 */
class BackupMigration(private val context: Context, private val openHelpers: () -> SupportSQLiteOpenHelper.Factory) {

    /**
     * [content] of an older version as it would be in this one. Fails as [BackupProblem.OlderVersion] if the app knows
     * no way from that version, and as [BackupProblem.Damaged] if the data do not fit how they were laid out then, or
     * the migrations fail on them.
     */
    fun upToDate(content: BackupContent): BackupContent {
        val migrations = migrationsFrom(content.schema)
        val layout = layoutOf(content.schema)
        inMemory().use { helper ->
            val db = helper.writableDatabase
            layout.forEach(db::execSQL)
            // In one transaction, as Room runs the migrations: they may count on it.
            db.beginTransaction()
            try {
                DataTables(db).replaceWith(content.tables)
                try {
                    migrations.forEach { it.migrate(db) }
                } catch (e: RuntimeException) {
                    // Such as a value an update would never have met, which a migration cannot read.
                    throw BackupException(BackupProblem.Damaged, e)
                }
                return BackupContent(HubDatabase.VERSION, content.created, DataTables(db).read())
            } finally {
                db.endTransaction()
            }
        }
    }

    /** The app's migrations from [version] up to this one, chosen as Room does: from each, the one that goes furthest. */
    private fun migrationsFrom(version: Int): List<Migration> = buildList {
        var reached = version
        while (reached < HubDatabase.VERSION) {
            val next = HubDatabase.MIGRATIONS
                .filter { it.startVersion == reached && it.endVersion <= HubDatabase.VERSION }
                .maxByOrNull { it.endVersion }
                ?: throw BackupException(BackupProblem.OlderVersion)
            add(next)
            reached = next.endVersion
        }
    }

    /** The statements that lay out the tables, indices and views of the database of [version], as Room exported it. */
    private fun layoutOf(version: Int): List<String> {
        val schema = try {
            context.assets.open("$SCHEMAS/$version.json").use { String(it.readBytes()) }
        } catch (e: FileNotFoundException) {
            throw BackupException(BackupProblem.OlderVersion, e)
        }
        val database = JSONObject(schema).getJSONObject("database")
        val tables = database.getJSONArray("entities").objects().flatMap { entity ->
            val table = entity.getString("tableName")
            val indices = entity.optJSONArray("indices")?.objects().orEmpty().map { index ->
                index.getString("createSql").replace(INDEX_NAME, index.getString("name")).replace(TABLE_NAME, table)
            }
            listOf(entity.getString("createSql").replace(TABLE_NAME, table)) + indices
        }
        val views = database.optJSONArray("views")?.objects().orEmpty().map { view ->
            view.getString("createSql").replace(VIEW_NAME, view.getString("viewName"))
        }
        return tables + views
    }

    /** Only in memory, so that nothing of the backup reaches the disk unencrypted. */
    private fun inMemory(): SupportSQLiteOpenHelper = openHelpers().create(
        SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(
                // Its own version plays no part: the layout comes from the schema.
                object : SupportSQLiteOpenHelper.Callback(1) {
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
        const val VIEW_NAME = "\${VIEW_NAME}"
    }
}
