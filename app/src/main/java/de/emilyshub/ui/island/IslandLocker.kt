package de.emilyshub.ui.island

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import de.emilyshub.island.Island

/**
 * When the screen locks [island] (SPEC.md §14, §17): as soon as it is [left], for another tab, the list of Sonstiges or
 * a screen over the tabs, and as soon as the activity stops, as when the app leaves the screen or the screen goes off;
 * but not while the activity is only [changingConfigurations], as for a rotation, after which the island shows as it was,
 * nor while the island sent the user out to the camera or the folder picker ([Island.goingOut]).
 */
class IslandLocker(private val island: Island<*>, private val changingConfigurations: () -> Boolean) : DefaultLifecycleObserver {

    /** The island is no longer shown. */
    fun left() {
        if (!changingConfigurations()) island.lock()
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!island.isOut) left()
    }
}
