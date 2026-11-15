package de.emilyshub.ui

import androidx.annotation.StringRes
import de.emilyshub.R

/**
 * What is listed under "Sonstiges" (SPEC.md §1), each opened in the tab, under the bottom bar: its [label] is the
 * title of the top bar then, and with the [emoji] in the list. An island with a lock of its own [locks] as soon as it
 * is left, for another tab too, and Sonstiges shows its list on return.
 */
enum class MoreEntry(val emoji: String, @param:StringRes val label: Int, val locks: Boolean = false) {
    Goals("🎯", R.string.destination_wishlist),

    /** Locked, with its own password (SPEC.md §14): it locks as soon as it is left. */
    Doku("🔒", R.string.destination_doku, locks = true),

    /** Sprachen (SPEC.md §15): the lessons held, by language and teacher. */
    Languages("🗣️", R.string.destination_languages),

    /** Locked, with a password of its own apart from the Doku's (SPEC.md §17), and the name tells nothing. */
    Tracking("📈", R.string.destination_tracking, locks = true),
}
