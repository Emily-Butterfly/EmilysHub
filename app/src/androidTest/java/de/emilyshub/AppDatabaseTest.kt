package de.emilyshub

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.data.Setting
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private val app = ApplicationProvider.getApplicationContext<EmilysHubApplication>()

    @Test
    fun `the app keeps its settings in an encrypted database`() {
        runBlocking { app.database.settings().put(Setting("instrumented-test", "ok")) }

        assertEquals("ok", runBlocking { app.database.settings().get("instrumented-test") })
        val header = app.getDatabasePath("emilyshub.db").readBytes().copyOf(15)
        assertFalse(header.contentEquals("SQLite format 3".toByteArray()))
    }
}
