package de.emilyshub.tracking

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * The photos of an event (SPEC.md §17, "Ereignisse"): kept in Tracking's own database, so encrypted with its key, in its
 * backups and gone with "Alles löschen". The fixture's clock stands at Sunday, 15 November 2026, 11:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EventPhotosTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val events = fixture.events

    @After
    fun close() = fixture.close()

    private fun create() = checkNotNull(runBlocking { events.create(EventKind.Shower) })

    @Test
    fun `a photo is kept shrunk, encoded anew, with the moment it was taken`() = runBlocking {
        val id = create()

        val added = events.addPhoto(id, jpeg(4000, 3000)) as PhotoAdd.Added

        val photos = checkNotNull(events.photos(id))
        assertEquals(listOf(added.id), photos.map { it.id })
        assertEquals(Instant.parse("2026-11-15T10:00:00Z"), photos.single().takenAt)
        val kept = checkNotNull(events.photo(added.id))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(kept, 0, kept.size, bounds)
        assertEquals(EventPhotos.MAX_EDGE, bounds.outWidth)
        assertEquals(EventPhotos.MAX_EDGE * 3 / 4, bounds.outHeight)
    }

    @Test
    fun `what is no picture is not kept, nor more than the most an event has`() = runBlocking {
        val id = create()

        assertEquals(PhotoAdd.Unreadable, events.addPhoto(id, "kein Bild".toByteArray()))
        repeat(EventPhotos.MAX_PER_EVENT) { assertTrue(events.addPhoto(id, jpeg(40, 30)) is PhotoAdd.Added) }
        assertEquals(PhotoAdd.TooMany, events.addPhoto(id, jpeg(40, 30)))
        assertEquals(EventPhotos.MAX_PER_EVENT, checkNotNull(events.photos(id)).size)
    }

    @Test
    fun `a photo deleted is gone, and all of an event go with it`() = runBlocking {
        val id = create()
        val first = (events.addPhoto(id, jpeg(40, 30)) as PhotoAdd.Added).id
        val second = (events.addPhoto(id, jpeg(40, 30)) as PhotoAdd.Added).id

        events.deletePhoto(first)
        assertEquals(listOf(second), checkNotNull(events.photos(id)).map { it.id })
        assertNull(events.photo(first))

        events.delete(id)
        assertNull(events.photo(second))
    }

    @Test
    fun `deleting all in Tracking deletes the photos`() = runBlocking {
        val id = create()
        val photo = (events.addPhoto(id, jpeg(40, 30)) as PhotoAdd.Added).id

        assertTrue(fixture.tracking.deleteAll("tracking-geheim"))

        assertNull(events.photo(photo))
    }

    @Test
    fun `none is kept once Tracking is locked`() = runBlocking {
        val id = create()
        fixture.tracking.lock()

        assertEquals(PhotoAdd.Locked, events.addPhoto(id, jpeg(40, 30)))
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }
}
