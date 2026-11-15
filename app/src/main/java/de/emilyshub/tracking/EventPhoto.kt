package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import java.time.Instant

/**
 * A photo of event [eventId] (SPEC.md §17, "Ereignisse"), taken at [takenAt], as a JPEG encoded anew ([EventPhotos]).
 * It lies in Tracking's own database, so that it is encrypted with its key as soon as it is kept, goes along in its
 * backups and is overwritten on disk once deleted, with "Alles löschen" too. Not a data class: its bytes compare by
 * content nowhere.
 */
@Entity(
    tableName = "event_photo",
    foreignKeys = [ForeignKey(entity = TrackingEvent::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("event_id")],
)
class EventPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "taken_at") val takenAt: Instant,
    val jpeg: ByteArray,
)

/** A photo of event [eventId] without its bytes, to be listed. */
data class PhotoInfo(
    val id: Long,
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "taken_at") val takenAt: Instant,
)

/** The photos of the events. */
@Dao
interface EventPhotoDao {
    /** The photos of event [eventId], the first taken first, without their bytes. */
    @Query("SELECT id, event_id, taken_at FROM event_photo WHERE event_id = :eventId ORDER BY taken_at, id")
    suspend fun of(eventId: Long): List<PhotoInfo>

    /** The photos of the events [eventIds], as [of]. */
    @Query("SELECT id, event_id, taken_at FROM event_photo WHERE event_id IN (:eventIds) ORDER BY taken_at, id")
    suspend fun ofEvents(eventIds: List<Long>): List<PhotoInfo>

    /** How many photos event [eventId] has. */
    @Query("SELECT COUNT(*) FROM event_photo WHERE event_id = :eventId")
    suspend fun count(eventId: Long): Int

    /** The JPEG of photo [id], if any. */
    @Query("SELECT jpeg FROM event_photo WHERE id = :id")
    suspend fun jpeg(id: Long): ByteArray?

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

    /** The event is gone. */
    data object NoEvent : PhotoAdd

    /** Tracking locked meanwhile: nothing was kept. */
    data object Locked : PhotoAdd
}

/**
 * How the photos of an event are kept (eigene Festlegung): no longer than [MAX_EDGE] at the long edge, enough to see
 * the skin on a doctor's screen, and small enough that many fit into Tracking's backup, which holds its whole database;
 * at most [MAX_PER_EVENT] to an event.
 */
object EventPhotos {
    /** The longest edge a photo keeps, in pixels. */
    const val MAX_EDGE = 1600

    /** The most photos an event has. */
    const val MAX_PER_EVENT = 6

    /** The largest photo taken that is read: 40 MB, more than any phone's camera makes. */
    const val MAX_TAKEN_BYTES = 40 * 1024 * 1024
}
