package de.emilyshub.reminders

import de.emilyshub.places.Area

/** Keeps the GPS areas the app has Android watch, for the test to see; Android itself is not asked. */
class FakeAreaWatch : AreaWatch {
    var watched: Set<Area> = emptySet()
        private set

    override fun watch(areas: Set<Area>) {
        watched = areas
    }
}
