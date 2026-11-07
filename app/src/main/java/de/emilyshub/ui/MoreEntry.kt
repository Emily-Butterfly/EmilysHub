package de.emilyshub.ui

import androidx.annotation.StringRes
import de.emilyshub.R

/**
 * What is listed under "Sonstiges" (SPEC.md §1), each opened in the tab, under the bottom bar: its [label] is the
 * title of the top bar then, and with the [emoji] in the list.
 */
enum class MoreEntry(val emoji: String, @param:StringRes val label: Int) {
    Goals("🎯", R.string.destination_wishlist),
}
