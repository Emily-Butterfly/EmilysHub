package de.emilyshub.ledger

import de.emilyshub.data.Saving

/**
 * Bookings made, changed and deleted in the tests as the booking editor does it ([Ledger.save], [Ledger.delete] of an
 * [BookingEditing]), so that the tests go through the interface the app uses, refusals and all.
 */

/** [draft] saved as a new booking; its id. Fails if it is refused. */
suspend fun Ledger.saveNew(draft: BookingDraft): Long = when (val saved = save(BookingEditing.New, draft)) {
    is Saving.Saved -> saved.id
    is Saving.Refused -> error("Refused: ${saved.problems}")
}

/** Booking [id] as its editor opens it, with [change] made to it, saved as the editor saves it. */
suspend fun Ledger.change(id: Long, change: (BookingDraft) -> BookingDraft): Saving<BookingProblem> {
    val editing = checkNotNull(editing(id))
    return save(editing, change(checkNotNull(editing.original)))
}

/** Booking [id] deleted as its editor deletes it; whether it was. */
suspend fun Ledger.deleteBooking(id: Long): Boolean = delete(checkNotNull(editing(id)))

/** Booking [id] as its editor opens it. */
suspend fun Ledger.savedDraft(id: Long): BookingDraft = checkNotNull(checkNotNull(editing(id)).original)
