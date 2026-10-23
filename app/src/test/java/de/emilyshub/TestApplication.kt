package de.emilyshub

import de.emilyshub.data.AppSettings
import de.emilyshub.data.InMemorySettingsDao
import de.emilyshub.data.inMemoryDatabase
import de.emilyshub.routines.Routines
import de.emilyshub.security.FakeAuthenticator
import kotlinx.coroutines.Dispatchers
import java.time.Instant
import java.time.ZoneId

/**
 * The app as every Robolectric test sees it (set in robolectric.properties): settings in memory and
 * the database schema in memory, both read and written on the spot, a clock the test sets, and a
 * fake unlock prompt.
 */
class TestApplication : EmilysHubApplication() {
    val settingsDao = InMemorySettingsDao()
    override val settings = AppSettings({ settingsDao }, Dispatchers.Unconfined)
    override val authenticator = FakeAuthenticator()

    /** Friday, 23 October 2026, 07:00 in Berlin. */
    val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))
    private val testDatabase by lazy { inMemoryDatabase() }
    override val routines = Routines({ testDatabase }, clock, Dispatchers.Unconfined)
}
