package de.emilyshub.tracking

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import de.emilyshub.island.SECURE_DELETE
import org.json.JSONObject
import java.io.File

/**
 * `tracking.db` in a file, so that what is in it outlives locking, but with Android's own SQLite, as SQLCipher's needs
 * its native library: the key is checked before, and here takes no part.
 */
fun testTrackingDatabase(context: Context): TrackingDatabase =
    Room.databaseBuilder(context, TrackingDatabase::class.java, TrackingDatabase.NAME)
        .allowMainThreadQueries()
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .addMigrations(*TrackingDatabase.MIGRATIONS.toTypedArray())
        .addCallback(SECURE_DELETE)
        .build()

/** `tracking.db` laid out as [version] had it, by the schema Room exported, and [fill]ed; with no key, as [testTrackingDatabase]. */
fun createTrackingAsExported(context: Context, version: Int, fill: (SQLiteDatabase) -> Unit) {
    val schema = JSONObject(File("schemas/de.emilyshub.tracking.TrackingDatabase/$version.json").readText()).getJSONObject("database")
    val file = context.getDatabasePath(TrackingDatabase.NAME).also { it.parentFile?.mkdirs() }
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
        db.version = version
        fill(db)
    }
}
