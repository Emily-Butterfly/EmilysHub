package de.emilyshub.tracking

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * The doctor's summary read from Tracking's database (SPEC.md §17, "Arzt-Zusammenfassung"), with made-up values only.
 * The fixture's clock stands at Sunday, 15 November 2026, 11:00 in Berlin.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TrackingSummaryTest {

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext())
    private val evaluation = fixture.evaluation
    private val today = LocalDate.parse("2026-11-15")

    @After
    fun close() = fixture.close()

    private fun morning(day: LocalDate, pulse: Int) = runBlocking {
        fixture.at("${day}T08:00:00Z")
        fixture.entries.saveMorning(MorningEntry(date = day, restingPulse = pulse, recordedOn = day), replacing = null)
        fixture.at("2026-11-15T10:00:00Z")
    }

    @Test
    fun `nothing tracked has no weeks, and the first entry counts them from its day`() = runBlocking {
        assertEquals(0, evaluation.weeksTracked())

        morning(today.minusDays(20), 70)
        assertEquals(3, evaluation.weeksTracked())

        fixture.at("2026-10-01T08:00:00Z")
        fixture.events.create(EventKind.Heat)
        fixture.at("2026-11-15T10:00:00Z")
        assertEquals("an event long before counts too", 7, evaluation.weeksTracked())
    }

    @Test
    fun `the summary of the weeks ending today, its photos only when chosen`() = runBlocking {
        morning(today.minusDays(3), 80)
        morning(today.minusDays(10), 100)
        val event = checkNotNull(fixture.events.create(EventKind.Shower))
        fixture.events.addPhoto(event, jpeg())

        val one = checkNotNull(evaluation.summary(weeks = 1, withPhotos = false))
        assertEquals(today.minusDays(6), one.from)
        assertEquals(today, one.to)
        assertEquals(Figure(80.0, 1), one.figures.pulseMean)
        assertEquals(listOf(event), one.events.map { it.id })
        assertEquals(emptyList<SummaryPhoto>(), one.photos)
        assertEquals("the basics are always among the hints", HintRule.Basics, one.hints.first { it.rule == HintRule.Basics }.rule)

        val two = checkNotNull(evaluation.summary(weeks = 2, withPhotos = true))
        assertEquals(Figure(90.0, 2), two.figures.pulseMean)
        assertEquals(listOf(event), two.photos.map { it.eventId })
    }

    @Test
    fun `one's own questions are kept for next time`() = runBlocking {
        assertEquals("", evaluation.questions())

        fixture.tracking.withDatabase { evaluation.writeQuestions(it, "- Woher kommt der Schwindel?") }

        assertEquals("- Woher kommt der Schwindel?", evaluation.questions())
        fixture.tracking.lock()
        assertNull(evaluation.questions())
    }

    private fun jpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }
}
