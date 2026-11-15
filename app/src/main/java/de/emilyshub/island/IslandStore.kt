package de.emilyshub.island

/**
 * Where an island's modules read and write (SPEC.md §14, §17): its database while open, nothing while locked. All they
 * need of the island; its key, lock and fingerprint stay behind this seam. Two adapters: [Island], the vault, and an
 * in-memory one in the tests, locked by a flag.
 */
interface IslandStore<D> {
    /** What [block] makes of the open database; null once the island is locked. */
    suspend fun <T> withDatabase(block: suspend (D) -> T): T?

    /** What [block] makes of the open database, as [withDatabase], counted as a change the pages read anew after. */
    suspend fun <T> changing(block: suspend (D) -> T): T?
}
