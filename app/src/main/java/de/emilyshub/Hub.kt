package de.emilyshub

import de.emilyshub.backup.Backups
import de.emilyshub.backup.NightlyBackups
import de.emilyshub.data.AppSettings
import de.emilyshub.ledger.Ledger
import de.emilyshub.notes.Notes
import de.emilyshub.places.CurrentLocation
import de.emilyshub.places.CurrentWifi
import de.emilyshub.places.Places
import de.emilyshub.reminders.ToneCollection
import de.emilyshub.routines.Routines
import de.emilyshub.search.Search
import de.emilyshub.security.AppLock
import de.emilyshub.security.DeviceAuthenticator
import de.emilyshub.tags.Tags
import de.emilyshub.today.Heute
import de.emilyshub.todos.Todos
import de.emilyshub.wishes.Wishes

/**
 * What the screens work with: the modules they show and change, and what of the device they ask. The composition root
 * ([EmilysHubApplication]) is it, and the UI takes it whole, so that a module reaches the screen that needs it without
 * being passed down through each layer above.
 */
interface Hub {
    val settings: AppSettings
    val appLock: AppLock
    val authenticator: DeviceAuthenticator
    val routines: Routines
    val todos: Todos
    val wishes: Wishes
    val notes: Notes
    val tags: Tags
    val places: Places
    val wifi: CurrentWifi
    val location: CurrentLocation
    val backups: Backups
    val nightlyBackups: NightlyBackups
    val search: Search
    val ledger: Ledger
    val toneCollection: ToneCollection
    val day: Day
    val heute: Heute

    /** A reminder to try, from "Funktioniert alles?" (SPEC.md §11). */
    fun probe()
}
