package de.emilyshub.backup

import de.emilyshub.doku.DokuSnapshot

/** What the backups need of the Doku (SPEC.md §10, §14): to take it along and put it back, never unlocking it. */
interface DokuVault {
    /** The Doku as it is now, holding together even while it is open; null while it has no password yet. */
    suspend fun snapshot(): DokuSnapshot?

    /** Puts [snapshot] in place of the Doku, then runs [during]; if that fails, the Doku is put back as it was. */
    suspend fun <T> replacedBy(snapshot: DokuSnapshot, during: suspend () -> T): T
}
