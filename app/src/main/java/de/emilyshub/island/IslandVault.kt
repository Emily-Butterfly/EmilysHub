package de.emilyshub.island


/** What the backups need of the island (SPEC.md §10, §14): to take it along and put it back, never unlocking it. */
interface IslandVault {
    /** The island as it is now, holding together even while it is open; null while it has no password yet. */
    suspend fun snapshot(): IslandSnapshot?

    /** Puts [snapshot] in place of the island, then runs [during]; if that fails, the island is put back as it was. */
    suspend fun <T> replacedBy(snapshot: IslandSnapshot, during: suspend () -> T): T
}
