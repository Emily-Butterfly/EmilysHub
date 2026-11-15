package de.emilyshub.doku

import de.emilyshub.island.Island
import de.emilyshub.island.IslandSaver
import de.emilyshub.island.IslandStore

/** The Doku's vault (SPEC.md §14): an [Island] around `doku.db`, under the Doku password. */
typealias Doku = Island<DokuDatabase>

/**
 * Where the Doku's modules read and write: its database while open, nothing while locked. Two adapters: [Doku], the
 * vault, and an in-memory one in the tests, locked by a flag.
 */
typealias DokuStore = IslandStore<DokuDatabase>

/** What an editor of the Doku has typed and not yet saved, to be saved before the Doku locks. */
typealias DokuSaver = IslandSaver<DokuDatabase>
