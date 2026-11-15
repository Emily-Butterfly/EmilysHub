package de.emilyshub.tracking

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import java.time.LocalDate
import java.time.LocalTime

/** What kind of load an exertion was (SPEC.md §17, "Anstrengungen, PEM & Crash"). */
enum class ExertionKind(val stored: String) { Physical("physical"), Cognitive("cognitive"), Emotional("emotional"), StandingHeat("standing_heat") }

/** How an exertion came about: by hand, from the evening's load, or confirmed from a suggestion. */
enum class ExertionOrigin(val stored: String) { Manual("manual"), Evening("evening"), Steps("steps"), HeartRate("heart_rate") }

/**
 * An exertion on [date], at [time] if known, of [kind], for [durationMinutes], of [strength] 0–3. One of [origin]
 * Evening follows its evening entry: it changes and goes with it.
 */
@Entity(tableName = "exertion", indices = [Index("date")])
data class Exertion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val time: LocalTime? = null,
    val kind: ExertionKind,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int? = null,
    val strength: Int,
    val origin: ExertionOrigin = ExertionOrigin.Manual,
)

/** What suggests an exertion, and what one confirmed is said to come from. */
enum class SuggestionSource(val stored: String, val origin: ExertionOrigin) {
    Steps("steps", ExertionOrigin.Steps),
    HeartRate("heart_rate", ExertionOrigin.HeartRate),
}

/** A suggestion of [source] for [date], decided: [confirmed] into an exertion, or dismissed; either way it does not come again. */
@Entity(tableName = "suggestion_decision", primaryKeys = ["date", "source"])
data class SuggestionDecision(val date: LocalDate, val source: SuggestionSource, val confirmed: Boolean)

/** "Schlechter als vor der Anstrengung?" */
enum class FollowUpAnswer(val stored: String) { Yes("yes"), No("no"), Unclear("unclear") }

/** What the evening entry of [date] answered about exertion [exertionId] of the days before. */
@Entity(
    tableName = "follow_up",
    primaryKeys = ["date", "exertion_id"],
    foreignKeys = [
        ForeignKey(entity = EveningEntry::class, parentColumns = ["date"], childColumns = ["date"], onDelete = ForeignKey.CASCADE, onUpdate = ForeignKey.CASCADE),
        ForeignKey(entity = Exertion::class, parentColumns = ["id"], childColumns = ["exertion_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exertion_id")],
)
data class FollowUp(val date: LocalDate, @ColumnInfo(name = "exertion_id") val exertionId: Long, val answer: FollowUpAnswer)

/** PEM, a noticeable worsening, or a crash, severe, the day largely given up. */
enum class EpisodeKind(val stored: String) { Pem("pem"), Crash("crash") }

/** How sure an exertion set off an episode. */
enum class Certainty(val stored: String) { Sure("sure"), Likely("likely"), Unclear("unclear") }

/**
 * An episode of PEM or a crash (SPEC.md §17): begun on [startDate] at about [startTime], ended on [endDate] or still
 * running, of [severity] 1–3; its suspected triggers apart ([EpisodeTrigger]).
 */
@Entity(tableName = "episode")
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EpisodeKind,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "start_time") val startTime: LocalTime? = null,
    @ColumnInfo(name = "end_date") val endDate: LocalDate? = null,
    val severity: Int? = null,
) {
    /** Whether it still runs: "läuft noch". */
    val running: Boolean get() = endDate == null
}

/** Exertion [exertionId] suspected to have set off episode [episodeId], with how sure. */
@Entity(
    tableName = "episode_trigger",
    primaryKeys = ["episode_id", "exertion_id"],
    foreignKeys = [
        ForeignKey(entity = Episode::class, parentColumns = ["id"], childColumns = ["episode_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exertion::class, parentColumns = ["id"], childColumns = ["exertion_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("exertion_id")],
)
data class EpisodeTrigger(
    @ColumnInfo(name = "episode_id") val episodeId: Long,
    @ColumnInfo(name = "exertion_id") val exertionId: Long,
    val certainty: Certainty,
)

/** The exertions and the suggestions decided; the follow-up answers belong to the evening ([EntryDao]). */
@Dao
interface ExertionDao {
    /** The exertions from [from] to [to], by date and time. */
    @Query("SELECT * FROM exertion WHERE date BETWEEN :from AND :to ORDER BY date, time, id")
    suspend fun between(from: LocalDate, to: LocalDate): List<Exertion>

    /** All exertions, the latest first. */
    @Query("SELECT * FROM exertion ORDER BY date DESC, time DESC, id DESC")
    suspend fun all(): List<Exertion>

    /** Exertion [id], if any. */
    @Query("SELECT * FROM exertion WHERE id = :id")
    suspend fun get(id: Long): Exertion?

    /** Adds [exertion]: its id. */
    @Insert
    suspend fun insert(exertion: Exertion): Long

    /** Writes [exertion] in place of the one of its id. */
    @Update
    suspend fun update(exertion: Exertion)

    /** Deletes [exertion]. */
    @Delete
    suspend fun delete(exertion: Exertion)

    /** The exertions taken from the evening entry of [date]. */
    @Query("SELECT * FROM exertion WHERE date = :date AND origin = 'evening'")
    suspend fun ofEvening(date: LocalDate): List<Exertion>

    /** The suggestions decided from [from] to [to]. */
    @Query("SELECT * FROM suggestion_decision WHERE date BETWEEN :from AND :to")
    suspend fun decisions(from: LocalDate, to: LocalDate): List<SuggestionDecision>

    /** Keeps [decision]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun decide(decision: SuggestionDecision)
}

/** The episodes and their triggers. */
@Dao
interface EpisodeDao {
    /** All episodes, the latest first. */
    @Query("SELECT * FROM episode ORDER BY start_date DESC, id DESC")
    suspend fun all(): List<Episode>

    /** Episode [id], if any. */
    @Query("SELECT * FROM episode WHERE id = :id")
    suspend fun get(id: Long): Episode?

    /** Adds [episode]: its id. */
    @Insert
    suspend fun insert(episode: Episode): Long

    /** Writes [episode] in place of the one of its id. */
    @Update
    suspend fun update(episode: Episode)

    /** Deletes episode [id] with its triggers. */
    @Query("DELETE FROM episode WHERE id = :id")
    suspend fun delete(id: Long)

    /** The triggers of episode [episodeId]. */
    @Query("SELECT * FROM episode_trigger WHERE episode_id = :episodeId")
    suspend fun triggers(episodeId: Long): List<EpisodeTrigger>

    /** Takes the triggers of episode [episodeId] off. */
    @Query("DELETE FROM episode_trigger WHERE episode_id = :episodeId")
    suspend fun clearTriggers(episodeId: Long)

    /** Adds [triggers], each in place of one for the same exertion. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTriggers(triggers: List<EpisodeTrigger>)
}
