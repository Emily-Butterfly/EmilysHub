package de.emilyshub.ui.doku

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.emilyshub.doku.Doku

/**
 * When the screen locks [doku] (SPEC.md §14): as soon as it is [left], for another tab, the list of Sonstiges or a
 * screen over the tabs, and as soon as the activity stops, as when the app leaves the screen or the screen goes off;
 * but not while the activity is only [changingConfigurations], as for a rotation, after which the Doku shows as it was.
 */
class DokuLocker(private val doku: Doku, private val changingConfigurations: () -> Boolean) : DefaultLifecycleObserver {

    /** The Doku is no longer shown. */
    fun left() {
        if (!changingConfigurations()) doku.lock()
    }

    override fun onStop(owner: LifecycleOwner) = left()
}
