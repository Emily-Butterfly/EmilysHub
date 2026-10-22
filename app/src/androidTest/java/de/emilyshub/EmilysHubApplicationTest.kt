package de.emilyshub

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmilysHubApplicationTest {

    private val app = ApplicationProvider.getApplicationContext<EmilysHubApplication>()

    @Test
    fun `the app opens its database encrypted`() {
        // Only reads, so nothing is left behind in the app's real database.
        assertNull(runBlocking { app.database.settings().get("not-a-setting") })

        // The file name is part of every installed copy of the app: renaming it would lose all data.
        val header = app.getDatabasePath("emilyshub.db").readBytes().copyOf(16)
        assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
        assertFalse(header.all { it == 0.toByte() })
    }
}
