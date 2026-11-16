package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import android.database.sqlite.SQLiteFullException
import de.emilyshub.island.IslandFileStore
import java.io.IOException
import java.time.Instant

/**
 * A photo of event [eventId] (SPEC.md §17, "Ereignisse"), taken at [takenAt], as a JPEG encoded anew ([EventPhotos]).
 * The JPEG lies in Tracking's own [file] ([IslandFileStore]), encrypted with a key from Tracking's as soon as it is
 * kept; it goes along in its backups as a record of its own and is deleted with "Alles löschen" too (ADR 0004).
 */
@Entity(
    tableName = "event_photo",
    foreignKeys = [ForeignKey(entity = TrackingEvent::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("event_id")],
)
data class EventPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "taken_at") val takenAt: Instant,
    val file: String,
)

/** A photo of event [eventId] without its file, to be listed. */
data class PhotoInfo(
    val id: Long,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "taken_at") val takenAt: Instant,
)

/** The photos of the events. */
@Dao
interface EventPhotoDao {
    /** The photos of event [eventId], the first taken first. */
    @Query("SELECT id, event_id, taken_at FROM event_photo WHERE event_id = :eventId ORDER BY taken_at, id")
    suspend fun of(eventId: Long): List<PhotoInfo>

    /** The photos of the events [eventIds], as [of]. */
    @Query("SELECT id, event_id, taken_at FROM event_photo WHERE event_id IN (:eventIds) ORDER BY taken_at, id")
    suspend fun ofEvents(eventIds: List<Long>): List<PhotoInfo>

    /** How many photos event [eventId] has. */
    @Query("SELECT COUNT(*) FROM event_photo WHERE event_id = :eventId")
    suspend fun count(eventId: Long): Int

    /** The file of photo [id], if any. */
    @Query("SELECT file FROM event_photo WHERE id = :id")
    suspend fun file(id: Long): String?

    /** The files of the photos of event [eventId]. */
    @Query("SELECT file FROM event_photo WHERE event_id = :eventId")
    suspend fun filesOf(eventId: Long): List<String>

    /** Adds [photo]: its id. */
    @Insert
    suspend fun insert(photo: EventPhoto): Long

    /** Deletes photo [id]. */
    @Query("DELETE FROM event_photo WHERE id = :id")
    suspend fun delete(id: Long)
}

/** What adding a photo to an event came to. */
sealed interface PhotoAdd {
    /** Kept as photo [id]. */
    data class Added(val id: Long) : PhotoAdd

    /** The event has [EventPhotos.MAX_PER_EVENT] already. */
    data object TooMany : PhotoAdd

    /** No picture Android can read, or larger than [EventPhotos.MAX_TAKEN_BYTES]. */
    data object Unreadable : PhotoAdd

    /** Its file could not be written, as the phone's storage is full: nothing was kept. */
    data object NotWritten : PhotoAdd

    /** The event is gone. */
    data object NoEvent : PhotoAdd

    /** Tracking locked meanwhile: nothing was kept. */
    data object Locked : PhotoAdd
}

/**
 * How the photos of an event are kept (eigene Festlegung): no longer than [MAX_EDGE] at the long edge, enough to see
 * the skin on a doctor's screen; at most [MAX_PER_EVENT] to an event.
 */
object EventPhotos {
    /** The longest edge a photo keeps, in pixels. */
    const val MAX_EDGE = 1600

    /** The most photos an event has. */
    const val MAX_PER_EVENT = 6

    /** The largest photo taken that is read: 40 MB, more than any phone's camera makes. */
    const val MAX_TAKEN_BYTES = 40 * 1024 * 1024

    /** Where the migration to version 10 left the photos that lay in the database before, by the file each goes to. */
    internal const val TO_MOVE = "photo_to_move"

    /**
     * Moves the photos that lay in Tracking's database before version 10 into their [files], once it is open: each
     * written, then its row deleted, overwritten on disk ([de.emilyshub.island.SECURE_DELETE]), so that nothing is lost
     * should the app stop meanwhile; one whose photo was deleted meanwhile only deleted. With the phone's storage full,
     * it stops, Tracking opens all the same and those left are read where they wait ([read]), to be moved the next time.
     */
    fun moveOutOfDatabase(database: TrackingDatabase, files: IslandFileStore) {
        val db = database.openHelper.writableDatabase
        if (!waiting(database)) return
        val names = db.query("SELECT file FROM $TO_MOVE").use { cursor -> List(cursor.count) { cursor.moveToPosition(it); cursor.getString(0) } }
        val kept = db.query("SELECT file FROM event_photo").use { cursor -> List(cursor.count) { cursor.moveToPosition(it); cursor.getString(0) } }.toSet()
        try {
            names.forEach { name ->
                if (name in kept) waitingJpeg(database, name)?.let { files.write(name, it) }
                db.execSQL("DELETE FROM $TO_MOVE WHERE file = ?", arrayOf(name))
            }
            db.execSQL("DROP TABLE $TO_MOVE")
        } catch (_: IOException) {
            // No room for the next file: the rest stay where they are.
        } catch (_: SQLiteFullException) {
            // No room for the database to write in.
        }
    }

    /** The photo in the file [name] of [files], or, not moved there yet, where it waits in [database]. */
    fun read(database: TrackingDatabase, files: IslandFileStore, name: String): ByteArray? =
        files.read(name) ?: if (waiting(database)) waitingJpeg(database, name) else null

    /** Whether photos wait in [database] to be moved into their files. */
    private fun waiting(database: TrackingDatabase): Boolean =
        database.openHelper.writableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$TO_MOVE'").use { it.count > 0 }

    private fun waitingJpeg(database: TrackingDatabase, name: String): ByteArray? =
        database.openHelper.writableDatabase.query("SELECT jpeg FROM $TO_MOVE WHERE file = ?", arrayOf(name)).use { if (it.moveToFirst()) it.getBlob(0) else null }
}
