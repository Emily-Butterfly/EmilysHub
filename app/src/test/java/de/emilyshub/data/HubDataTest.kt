package de.emilyshub.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** The app's data as they lie on the phone (#100): opened or why not, laid aside rather than deleted, and put back. */
@RunWith(AndroidJUnit4::class)
class HubDataTest {

    @get:Rule
    val disk = TemporaryFolder()

    private val clock = TestClock(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Europe/Berlin"))

    /** Why opening fails next, none for it to open. */
    private var fails: Unopenable? = null
    private var opens = 0
    private var replaced = 0

    private val databaseFile by lazy { disk.root.resolve("databases/emilyshub.db") }
    private val keyFile by lazy { disk.root.resolve("no_backup/database.key") }
    private val attachments by lazy { disk.root.resolve("files/attachments") }
    private val doku by lazy { disk.root.resolve("databases/doku.db") }
    private val setAsideFolder by lazy { disk.root.resolve("no_backup/set-aside") }

    private val data by lazy {
        HubData(
            open = {
                opens++
                fails?.let { throw DataUnavailable(it) }
                // As the real one does, which makes them anew where there are none.
                if (!keyFile.exists()) write(keyFile, "new key")
                if (!databaseFile.exists()) write(databaseFile, "new data")
                inMemoryDatabase()
            },
            files = listOf(databaseFile, keyFile, attachments),
            setAsideFolder = setAsideFolder,
            clock = clock,
            io = Dispatchers.Unconfined,
            onReplaced = { replaced++ },
        )
    }

    private fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    private fun broken() {
        write(databaseFile, "old data")
        write(keyFile, "old key")
        write(attachments.resolve("a.enc"), "old attachment")
        write(doku, "doku")
    }

    @Test
    fun `opened, the data are the same database each time`() = runBlocking {
        assertNull(data.check())
        assertSame(data.database, data.database)
        assertEquals(1, opens)
    }

    @Test
    fun `failing, it says why each time, without trying again until asked`() = runBlocking {
        for (reason in Unopenable.entries) {
            fails = reason
            assertEquals(reason, data.retry())
            assertEquals(reason, data.check())
            try {
                data.database
                fail("opened")
            } catch (e: DataUnavailable) {
                assertEquals(reason, e.reason)
            }
        }
        val tries = opens

        fails = null
        data.check()
        assertEquals("not tried again", tries, opens)
        assertNull(data.retry())
    }

    @Test
    fun `anything else that fails on opening counts as a damaged file`() = runBlocking {
        val failing = HubData({ error("not a database") }, emptyList(), setAsideFolder, clock, Dispatchers.Unconfined)

        assertEquals(Unopenable.FileBroken, failing.check())
    }

    @Test
    fun `begun anew and kept, the old files lie aside with their date, and new data take their place`() = runBlocking {
        broken()
        fails = Unopenable.KeyBroken
        data.check()

        fails = null
        data.beginAnew()
        data.keep()

        assertEquals("new data", databaseFile.readText())
        assertEquals("new key", keyFile.readText())
        assertFalse(attachments.exists())
        assertEquals("doku", doku.readText())
        val laid = data.setAside().single()
        assertEquals(clock.now, laid.at)
        val folder = setAsideFolder.resolve(laid.id)
        assertEquals(setOf("old data", "old key"), folder.listFiles()!!.filter { it.isFile }.map { it.readText() }.toSet())
        assertEquals("old attachment", folder.walk().single { it.name == "a.enc" }.readText())
        assertEquals(1, replaced)
    }

    @Test
    fun `undone, everything is as it was, and still cannot be opened`() = runBlocking {
        broken()
        fails = Unopenable.FileBroken
        data.check()

        fails = null
        data.beginAnew()
        fails = Unopenable.FileBroken
        data.undo()

        assertEquals("old data", databaseFile.readText())
        assertEquals("old key", keyFile.readText())
        assertEquals("old attachment", attachments.resolve("a.enc").readText())
        assertEquals(emptyList<SetAside>(), data.setAside())
        assertEquals(Unopenable.FileBroken, data.check())
        assertEquals(2, replaced)
    }

    @Test
    fun `the empty data failing to open as well, everything is put back as it was`() = runBlocking {
        broken()
        fails = Unopenable.KeyBroken

        try {
            data.beginAnew()
            fail("began anew")
        } catch (e: DataUnavailable) {
            assertEquals(Unopenable.KeyBroken, e.reason)
        }

        assertEquals("old data", databaseFile.readText())
        assertEquals(emptyList<SetAside>(), data.setAside())
    }

    @Test
    fun `ended between beginning anew and keeping, the next start puts everything as it was`() = runBlocking {
        broken()
        data.beginAnew()
        assertEquals("new data", databaseFile.readText())

        // The app ends here; the next start opens the data anew.
        val next = HubData({ inMemoryDatabase() }, listOf(databaseFile, keyFile, attachments), setAsideFolder, clock, Dispatchers.Unconfined)
        assertNull(next.check())

        assertEquals("old data", databaseFile.readText())
        assertEquals("old key", keyFile.readText())
        assertEquals(emptyList<SetAside>(), next.setAside())
    }

    @Test
    fun `ended while laying aside, what was not moved yet stays, and what was is put back`() = runBlocking {
        broken()
        // As the app left it: the mark set and the database moved, but not yet the key or the attachments.
        val folder = setAsideFolder.resolve("1").apply { mkdirs() }
        folder.resolve("pending").createNewFile()
        databaseFile.renameTo(folder.resolve("0-emilyshub.db"))

        assertNull(data.check())

        assertEquals("old data", databaseFile.readText())
        assertEquals("old key", keyFile.readText())
        assertEquals("old attachment", attachments.resolve("a.enc").readText())
    }

    @Test
    fun `laid aside twice within the same millisecond, each lies aside on its own`() = runBlocking {
        broken()
        data.beginAnew()
        data.keep()
        data.beginAnew()
        data.keep()

        assertEquals(2, data.setAside().size)
    }

    @Test
    fun `deleted for good, data laid aside are gone, and the others stay`() = runBlocking {
        broken()
        data.beginAnew()
        data.keep()
        clock.now = clock.now.plusSeconds(60)
        data.beginAnew()
        data.keep()
        val (newer, older) = data.setAside()
        assertTrue(newer.at.isAfter(older.at))

        data.delete(older)

        assertEquals(listOf(newer), data.setAside())
        assertFalse(setAsideFolder.resolve(older.id).exists())
    }
}
