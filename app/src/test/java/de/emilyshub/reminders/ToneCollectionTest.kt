package de.emilyshub.reminders

import de.emilyshub.data.InMemorySettingsDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ToneCollectionTest {

    private val tones = FakeTones()
    private val files = FakeToneFiles(tones)
    private val collection = ToneCollection(tones, files, InMemorySettingsDao().let { dao -> { dao } })

    @Test
    fun `a file of its own is copied into the phone's tones, and the choice is the copy`() {
        files.add("content://downloads/1", "Morgenlied.mp3")

        val added = add("content://downloads/1", ToneKind.Alarm)

        val copy = files.copies.single()
        assertEquals(ToneAdding.Added(Tone.Of(copy.uri)), added)
        assertEquals(FakeToneFiles.Copy("Morgenlied.mp3", ToneKind.Alarm, copy.uri), copy)
        assertEquals("Morgenlied", collection.title(copy.uri))
    }

    @Test
    fun `the same file chosen twice is copied once, also under another name`() {
        files.add("content://downloads/1", "Morgenlied.mp3", bytes = SONG)
        files.add("content://downloads/2", "Kopie von Morgenlied.mp3", bytes = SONG)

        val first = add("content://downloads/1")
        val second = add("content://downloads/1")
        val third = add("content://downloads/2")

        assertEquals(1, files.copies.size)
        assertEquals(first, second)
        assertEquals(first, third)
    }

    @Test
    fun `a copy deleted from the phone meanwhile is made anew`() {
        files.add("content://downloads/1", "Morgenlied.mp3")
        val first = add("content://downloads/1") as ToneAdding.Added
        tones.remove(first.tone.uri)

        val again = add("content://downloads/1")

        assertEquals(2, files.copies.size)
        assertEquals(ToneAdding.Added(Tone.Of(files.copies.last().uri)), again)
    }

    @Test
    fun `a file of up to 20 MB is taken, a larger one is turned down and not copied`() {
        files.add("content://downloads/1", "Lang.mp3", bytes = ByteArray(ToneCollection.MAX_BYTES))
        files.add("content://downloads/2", "Zu lang.mp3", bytes = ByteArray(ToneCollection.MAX_BYTES + 1))

        assertEquals(ToneAdding.TooLarge, add("content://downloads/2"))
        assertEquals(emptyList<FakeToneFiles.Copy>(), files.copies)
        assertEquals(ToneAdding.Added::class, add("content://downloads/1")::class)
    }

    @Test
    fun `a file that does not play as audio, or cannot be read, is turned down and not copied`() {
        files.add("content://downloads/1", "Liste.pdf", playable = false)

        assertEquals(ToneAdding.NotAudio, add("content://downloads/1"))
        assertEquals(ToneAdding.NotAudio, add("content://downloads/gone"))
        assertEquals(emptyList<FakeToneFiles.Copy>(), files.copies)
    }

    private fun add(file: String, kind: ToneKind = ToneKind.Any) = runBlocking { collection.add(file, kind) }

    private companion object {
        val SONG = ByteArray(1000) { it.toByte() }
    }
}
