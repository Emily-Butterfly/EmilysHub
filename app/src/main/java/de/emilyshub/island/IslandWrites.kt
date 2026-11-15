package de.emilyshub.island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Writes into an [island] in the [background], for a page that goes away with something not saved yet: it outlives
 * the page. Nothing once the island is locked: what was not saved by then was saved as it locked ([IslandSaver]).
 */
class IslandWrites<D>(private val island: IslandStore<D>, private val background: CoroutineScope) {
    /** Writes [write] once it can, counted as a change. */
    fun later(write: suspend (D) -> Unit) {
        background.launch { island.changing(write) }
    }
}
