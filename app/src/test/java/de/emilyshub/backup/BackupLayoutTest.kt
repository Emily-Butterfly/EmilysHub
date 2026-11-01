package de.emilyshub.backup

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.data.HubDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tables of the app's data and their columns in each version (SPEC.md §10), as Room exported its schema: what a
 * backup of that version holds, without a list of them kept by hand.
 */
@RunWith(AndroidJUnit4::class)
class BackupLayoutTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val migration = BackupMigration(app) { FrameworkSQLiteOpenHelperFactory() }

    @Test
    fun `a version's data are the tables and columns of its schema, the settings aside`() {
        val version16 = migration.dataColumnsOf(16)

        assertFalse("the wishes came with 17", "wishes" in version16)
        assertFalse("the settings belong to the device", "settings" in version16)
        assertTrue("title" in version16.getValue("todos"))
        assertFalse("goals came with 18", "goal_id" in version16.getValue("todos"))
    }

    @Test
    fun `this version's are those of the app's own database, so that its schema was exported as it is`() {
        val tables = DataTables(app.testDatabase.openHelper.writableDatabase).read()

        assertEquals(
            tables.mapValues { (_, rows) -> rows.columns.toSet() },
            migration.dataColumnsOf(HubDatabase.VERSION).mapValues { (_, columns) -> columns.toSet() },
        )
    }
}
