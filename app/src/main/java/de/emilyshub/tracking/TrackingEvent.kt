package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.time.Duration
import java.time.Instant

/** What set off an event (SPEC.md §17, "Ereignisse"); [water] if it has a water temperature. */
enum class EventKind(val stored: String, val water: Boolean) {
    Shower("shower", water = true),
    Bath("bath", water = true),
    Sauna("sauna", water = false),
    Heat("heat", water = false),
    LongStanding("long_standing", water = false),
    Other("other", water = false),
}

/** How warm the water was. */
enum class WaterTemperature(val stored: String) { Lukewarm("lukewarm"), Warm("warm"), Hot("hot") }

/** Standing or sitting during the event. */
enum class Posture(val stored: String) { Standing("standing"), Sitting("sitting") }

/** Where the skin changed. */
enum class SkinArea(val stored: String) { Legs("legs"), Arms("arms"), Trunk("trunk"), FingersToes("fingers_toes") }

/** What the change of the skin looked like: even and closed, or broken and net-like. */
enum class SkinPattern(val stored: String) { Even("even"), Reticular("reticular") }

/**
 * An event that set off complaints (SPEC.md §17, "Ereignisse"), of [kind] at [at], to the minute; for water how warm,
 * how long, standing or sitting. The pulse before, right after its end ([endedAt], which starts the timer), after 5
 * and after 10 minutes; the oxygen saturation, breathlessness and dizziness 0–3, whether one had to sit down and after
 * how many minutes. The skin: where, its pattern, whether it hurt, whether it faded lying down and in how many minutes;
 * the minutes until one could bear a load again. Everything can be added later; what was not filled in is null.
 */
@Entity(tableName = "tracking_event")
data class TrackingEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EventKind,
    val at: Instant,
    val water: WaterTemperature? = null,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int? = null,
    val posture: Posture? = null,
    @ColumnInfo(name = "pulse_before") val pulseBefore: Int? = null,
    @ColumnInfo(name = "ended_at") val endedAt: Instant? = null,
    @ColumnInfo(name = "pulse_after") val pulseAfter: Int? = null,
    @ColumnInfo(name = "pulse_five") val pulseFive: Int? = null,
    @ColumnInfo(name = "pulse_ten") val pulseTen: Int? = null,
    val spo2: Int? = null,
    val breathlessness: Int? = null,
    val dizziness: Int? = null,
    @ColumnInfo(name = "sat_down") val satDown: Boolean? = null,
    @ColumnInfo(name = "sat_down_after_minutes") val satDownAfterMinutes: Int? = null,
    @ColumnInfo(name = "skin_areas") val skinAreas: Set<SkinArea> = emptySet(),
    @ColumnInfo(name = "skin_pattern") val skinPattern: SkinPattern? = null,
    @ColumnInfo(name = "skin_painful") val skinPainful: Boolean? = null,
    @ColumnInfo(name = "skin_fades_lying") val skinFadesLying: Boolean? = null,
    @ColumnInfo(name = "skin_fade_minutes") val skinFadeMinutes: Int? = null,
    @ColumnInfo(name = "recovered_minutes") val recoveredMinutes: Int? = null,
)

/** How long before event [eventId] medication [medicationId] was last taken today, as entered. */
@Entity(
    tableName = "event_intake",
    primaryKeys = ["event_id", "medication_id"],
    foreignKeys = [
        ForeignKey(entity = TrackingEvent::class, parentColumns = ["id"], childColumns = ["event_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medication_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medication_id")],
)
data class EventIntake(
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "medication_id") val medicationId: Long,
    @ColumnInfo(name = "minutes_before") val minutesBefore: Int,
)

/** The events and the intakes before them. */
@Dao
interface EventDao {
    /** When the first event was, if any. */
    @Query("SELECT MIN(at) FROM tracking_event")
    suspend fun first(): Instant?

    /** All events, the latest first; their times are kept to the minute, so that they sort as text. */
    @Query("SELECT * FROM tracking_event ORDER BY at DESC, id DESC")
    suspend fun all(): List<TrackingEvent>

    /** Event [id], if any. */
    @Query("SELECT * FROM tracking_event WHERE id = :id")
    suspend fun get(id: Long): TrackingEvent?

    /** Adds [event]: its id. */
    @Insert
    suspend fun insert(event: TrackingEvent): Long

    /** Writes [event] in place of the one of its id. */
    @Update
    suspend fun update(event: TrackingEvent)

    /** Deletes event [id], with its intakes. */
    @Query("DELETE FROM tracking_event WHERE id = :id")
    suspend fun delete(id: Long)

    /** Adds [intakes]. */
    @Insert
    suspend fun insertIntakes(intakes: List<EventIntake>)

    /** Takes the intakes before event [eventId] off. */
    @Query("DELETE FROM event_intake WHERE event_id = :eventId")
    suspend fun clearIntakes(eventId: Long)

    /** The intakes before event [eventId]. */
    @Query("SELECT * FROM event_intake WHERE event_id = :eventId")
    suspend fun intakes(eventId: Long): List<EventIntake>
}

/** The pulses of an event taken with the timer, by the minutes after its end. */
enum class EventPulse(val minutes: Long) { After(0), Five(5), Ten(10) }

/**
 * The pulse timer of an event (SPEC.md §17, "Ereignisse"), pure logic: from the end of the event the pulse right after,
 * after 5 and after 10 minutes. It runs until the last is entered, at the longest [GRACE] after it was due; meanwhile
 * the measuring assistant's exception holds.
 */
object EventTimer {
    /** How long after the ten minutes the timer still waits for their pulse. */
    val GRACE: Duration = Duration.ofMinutes(5)

    /** When [pulse] is due, for an event that ended at [endedAt]. */
    fun dueAt(endedAt: Instant, pulse: EventPulse): Instant = endedAt.plus(Duration.ofMinutes(pulse.minutes))

    /** Whether [pulse] is due at [now]. */
    fun isDue(endedAt: Instant, pulse: EventPulse, now: Instant): Boolean = !now.isBefore(dueAt(endedAt, pulse))

    /** The first pulse of [event] not entered yet; none once all are. */
    fun next(event: TrackingEvent): EventPulse? = EventPulse.entries.firstOrNull { valueOf(event, it) == null }

    /** Whether the timer of [event] runs at [now]: it ended, a pulse is still to come, and not long past the ten minutes. */
    fun runs(event: TrackingEvent, now: Instant): Boolean {
        val ended = event.endedAt ?: return false
        return next(event) != null && now.isBefore(dueAt(ended, EventPulse.Ten).plus(GRACE))
    }

    /** The pulse of [event] at [pulse]. */
    fun valueOf(event: TrackingEvent, pulse: EventPulse): Int? = when (pulse) {
        EventPulse.After -> event.pulseAfter
        EventPulse.Five -> event.pulseFive
        EventPulse.Ten -> event.pulseTen
    }
}
