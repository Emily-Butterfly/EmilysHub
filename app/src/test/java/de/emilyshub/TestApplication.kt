package de.emilyshub

import de.emilyshub.data.AppSettings
import de.emilyshub.data.InMemorySettingsDao
import de.emilyshub.security.FakeAuthenticator
import kotlinx.coroutines.Dispatchers

/**
 * The app as every Robolectric test sees it (set in robolectric.properties): settings in memory,
 * read and written on the spot, and a fake unlock prompt.
 */
class TestApplication : EmilysHubApplication() {
    val settingsDao = InMemorySettingsDao()
    override val settings = AppSettings({ settingsDao }, Dispatchers.Unconfined)
    override val authenticator = FakeAuthenticator()
}
