package de.emilyshub.tracking

import java.time.Clock
import java.time.LocalDate

/** An exertion to choose as a trigger of an episode, with the [gap] from it to the episode's start. */
data class Candidate(val exertion: Exertion, val gap: Gap)

/** An episode on its page: the [episode], its [triggers] by exertion with how sure, and the [candidates] to choose from. */
data class EpisodeSheet(val episode: Episode, val triggers: Map<Long, Certainty>, val candidates: List<Candidate>)

/**
 * The episodes of PEM and crash in Tracking (SPEC.md §17): begun from the start page or from "Crash oder PEM?" in an
 * evening entry, their suspected triggers chosen from the exertions of the three days before the start. Read and
 * written only while Tracking is open.
 */
class TrackingEpisodes(private val tracking: TrackingStore, private val clock: Clock) {

    /** A new episode of [kind] beginning on [day]: its id; none once locked. */
    suspend fun create(kind: EpisodeKind, day: LocalDate): Long? = tracking.changing { it.episodes().insert(Episode(kind = kind, startDate = day)) }

    /** Episode [id] for its page; none if gone, or once locked. */
    suspend fun sheet(id: Long): EpisodeSheet? = tracking.withDatabase { database -> sheetOf(database, id) }

    private suspend fun sheetOf(database: TrackingDatabase, id: Long): EpisodeSheet? {
        val episode = database.episodes().get(id) ?: return null
        return EpisodeSheet(
            episode = episode,
            triggers = database.episodes().triggers(id).associate { it.exertionId to it.certainty },
            candidates = candidatesOf(database, episode),
        )
    }

    /** The exertions to choose as triggers of [episode] as it stands, its start changed say; none once locked. */
    suspend fun candidates(episode: Episode): List<Candidate>? = tracking.withDatabase { database -> candidatesOf(database, episode) }

    private suspend fun candidatesOf(database: TrackingDatabase, episode: Episode): List<Candidate> {
        val nearby = database.exertions().between(episode.startDate.minusDays(ExertionRules.TRIGGER_DAYS), episode.startDate)
        return ExertionRules.candidates(episode, nearby).map { Candidate(it, ExertionRules.hoursAfter(it, episode)) }
    }

    /** Saves [episode] with its [triggers], as [write] does; false once locked. */
    suspend fun save(episode: Episode, triggers: Map<Long, Certainty>): Boolean = tracking.changing { write(it, episode, triggers) } ?: false

    /**
     * Writes [episode] into [database] in place of the one of its id, with [triggers] in place of those it had, only
     * those of the exertions to choose from by its start; its severity off its scale and an end before its start are
     * left out. False if it is gone.
     */
    suspend fun write(database: TrackingDatabase, episode: Episode, triggers: Map<Long, Certainty>): Boolean {
        database.episodes().get(episode.id) ?: return false
        val cleaned = episode.copy(
            severity = episode.severity?.takeIf { it in ExertionRules.SEVERITY },
            endDate = episode.endDate?.takeIf { !it.isBefore(episode.startDate) },
        )
        database.episodes().update(cleaned)
        val allowed = candidatesOf(database, cleaned).map { it.exertion.id }.toSet()
        database.episodes().clearTriggers(cleaned.id)
        database.episodes().putTriggers(triggers.filterKeys { it in allowed }.map { (exertion, certainty) -> EpisodeTrigger(cleaned.id, exertion, certainty) })
        return true
    }

    /** Ends episode [id] today: "beenden". */
    suspend fun end(id: Long) {
        tracking.changing { database ->
            val episode = database.episodes().get(id)?.takeIf { it.running } ?: return@changing
            val today = LocalDate.now(clock)
            database.episodes().update(episode.copy(endDate = maxOf(today, episode.startDate)))
        }
    }

    /** Deletes episode [id] with its triggers; the exertions stay. */
    suspend fun delete(id: Long) {
        tracking.changing { it.episodes().delete(id) }
    }

    /** The episodes still running, for the start page; none once locked. */
    suspend fun running(): List<Episode>? = tracking.withDatabase { database -> database.episodes().all().filter { it.running } }

    /** All episodes, the latest first; none once locked. */
    suspend fun list(): List<Episode>? = tracking.withDatabase { it.episodes().all() }
}
