package de.emilyshub

import de.emilyshub.data.AppSettings
import de.emilyshub.data.InMemorySettingsDao
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.places.FakeLocation
import de.emilyshub.places.FakeWifi
import de.emilyshub.places.Locator
import de.emilyshub.places.Places
import de.emilyshub.reminders.FakeRinger
import de.emilyshub.reminders.Reminders
import de.emilyshub.routines.Routines
import de.emilyshub.security.FakeAuthenticator
import de.emilyshub.tags.Tags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId

/**
 * The app as every Robolectric test sees it (set in robolectric.properties): settings in memory and
 * the database schema in memory, both read and written on the spot, a clock the test sets, work in the
 * background done on the spot too, a fake unlock prompt, a full-screen alarm that rings without a sound, a WLAN
 * the test connects to, and a position the test sets.
 */
class TestApplication : EmilysHubApplication() {
    val settingsDao = InMemorySettingsDao()
    override val settings = AppSettings({ settingsDao }, Dispatchers.Unconfined)
    override val authenticator = FakeAuthenticator()
    override val ringer = FakeRinger()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val testDatabase by lazy { inMemoryDatabase() }
    override val routines: Routines = Routines({ testDatabase }, clock, Dispatchers.Unconfined, onChange = ::updateReminders)
    override val tags = Tags({ testDatabase }, Dispatchers.Unconfined)
    override val places = Places({ testDatabase }, Dispatchers.Unconfined, onChange = ::updateReminders)
    override val wifi = FakeWifi()
    override val location = FakeLocation()
    override val locator = Locator(wifi, location, places)
    override val reminders: Reminders by lazy { Reminders(this, routines, clock, locator::whereabouts) { settingsDao } }
    override val background = CoroutineScope(Dispatchers.Unconfined)

    /** Whether Android lets the app fill the screen with a reminder ([ShadowFullScreenIntents]); not until a test says. */
    var fullScreenAlarmsAllowed = false
}
