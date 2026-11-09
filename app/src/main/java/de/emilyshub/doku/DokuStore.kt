package de.emilyshub.doku

/**
 * Where the Doku's modules read and write (SPEC.md §14): its database while open, nothing while locked. All they need of
 * the Doku; its key, lock and fingerprint stay behind this seam. Two adapters: [Doku], the vault, and an in-memory
 * one in the tests, locked by a flag.
 */
interface DokuStore {
    /** What [block] makes of the open database; null once the Doku is locked. */
    suspend fun <T> withDatabase(block: suspend (DokuDatabase) -> T): T?

    /** What [block] makes of the open database, as [withDatabase], counted as a change the pages read anew after. */
    suspend fun <T> changing(block: suspend (DokuDatabase) -> T): T?
}
