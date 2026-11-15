package de.emilyshub.tracking

import de.emilyshub.island.Island
import de.emilyshub.island.IslandStore

/** Tracking's vault (SPEC.md §17): an [Island] around `tracking.db`, under the Tracking password. */
typealias Tracking = Island<TrackingDatabase>

/** Where Tracking's modules read and write: its database while open, nothing while locked. */
typealias TrackingStore = IslandStore<TrackingDatabase>
