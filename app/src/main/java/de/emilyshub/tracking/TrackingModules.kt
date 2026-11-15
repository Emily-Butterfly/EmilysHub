package de.emilyshub.tracking

import de.emilyshub.island.IslandWrites

/** What Tracking's pages read and write, each only while it is open (SPEC.md §17), and how they save as they go. */
data class TrackingModules(
    val entries: TrackingEntries,
    val symptoms: TrackingSymptoms,
    val medications: TrackingMedications,
    val baseData: TrackingBaseData,
    val garmin: TrackingGarmin,
    val standTests: TrackingStandTests,
    val events: TrackingEvents,
    val exertions: TrackingExertions,
    val episodes: TrackingEpisodes,
    val evaluation: TrackingEvaluation,
    val writes: IslandWrites<TrackingDatabase>,
)
