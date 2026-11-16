package de.emilyshub.ui.tracking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.TestApplication
import de.emilyshub.tracking.DoctorSummary
import de.emilyshub.tracking.EventKind
import de.emilyshub.tracking.Symptom
import de.emilyshub.tracking.SymptomValue
import de.emilyshub.tracking.SummaryPhoto
import de.emilyshub.tracking.TrackingEvent
import de.emilyshub.tracking.TrackingRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import de.emilyshub.tracking.Funcap
import de.emilyshub.tracking.FuncapForm
import de.emilyshub.tracking.FuncapResult
import de.emilyshub.tracking.FuncapSummary
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The doctor's summary laid out on A4 pages (SPEC.md §17), as many as it needs, black on white, with made-up values
 * only. Drawn on bitmaps here, as Robolectric has no PdfDocument; the PDF itself is checked on a device
 * (SummaryPdfDeviceTest).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SummaryPdfTest {

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val berlin = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.parse("2026-11-15")

    /** [blocks] laid out on bitmaps of A4 in points: the pages. */
    private fun pages(blocks: List<SummaryBlock>): List<Bitmap> {
        val drawn = mutableListOf<Bitmap>()
        val count = SummaryPdf.layOut(
            blocks,
            object : SummaryPages {
                override fun start(number: Int): Canvas {
                    assertEquals(drawn.size + 1, number)
                    val page = Bitmap.createBitmap(SummaryPdf.PAGE_WIDTH, SummaryPdf.PAGE_HEIGHT, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    drawn += page
                    return Canvas(page)
                }

                override fun finish() = Unit
            },
        ) { "Seite $it" }
        assertEquals(drawn.size, count)
        return drawn
    }

    /** Whether [page] has anything drawn on it, and nothing but grey. */
    private fun inkOnly(page: Bitmap): Pair<Boolean, Boolean> {
        var ink = false
        var grey = true
        for (x in 0 until page.width step 2) for (y in 0 until page.height step 2) {
            val pixel = page.getPixel(x, y)
            if (pixel != Color.WHITE) ink = true
            if (Color.red(pixel) != Color.green(pixel) || Color.green(pixel) != Color.blue(pixel)) grey = false
        }
        return ink to grey
    }

    @Test
    fun `an empty summary still has its sections, every page drawn black on white`() {
        val summary = DoctorSummary.of(TrackingRecords(berlin), today.minusDays(27), today, hints = emptyList())

        val pages = pages(summaryBlocks(app.resources, summary, "", berlin, today))

        assertTrue(pages.size in 1..2)
        assertTrue(pages.all { inkOnly(it) == (true to true) })
    }

    @Test
    fun `a long table runs on over pages, and photos go along when given`() {
        val events = (1..120).map { TrackingEvent(id = it.toLong(), kind = EventKind.Shower, at = Instant.parse("2026-11-14T08:00:00Z"), pulseBefore = 80) }
        val photo = SummaryPhoto(1, Instant.parse("2026-11-14T08:05:00Z"), jpeg())
        val symptoms = listOf(Symptom(id = 1, name = "Symptom A", position = 0))
        val records = TrackingRecords(berlin, events = events, symptoms = symptoms, symptomValues = listOf(SymptomValue(today, 1, 2)))
        val without = DoctorSummary.of(records, today.minusDays(27), today, hints = emptyList())
        val with = DoctorSummary.of(records, today.minusDays(27), today, hints = emptyList(), photos = listOf(photo))

        val pages = pages(summaryBlocks(app.resources, without, "- Eigene Frage?", berlin, today))
        val withPhotos = pages(summaryBlocks(app.resources, with, "", berlin, today))

        assertTrue("${pages.size} pages", pages.size >= 3)
        assertTrue("every page drawn on, in black and white", pages.all { inkOnly(it) == (true to true) })
        assertTrue("the photo takes room", withPhotos.size >= pages.size)
    }

    private fun jpeg(): ByteArray {
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }.compress(Bitmap.CompressFormat.JPEG, 80, out)
        return out.toByteArray()
    }

    @Test
    fun `the monthly questionnaire with the one before and the evenings' means beside it`() {
        val form = { id: Long, day: LocalDate, value: Int ->
            FuncapResult(FuncapForm(id = id, startedOn = day, completedOn = day), Funcap.score(Funcap.DOMAINS.associate { it.items.first().number to value }))
        }
        val fatigue = Symptom(id = 1, name = "Erschöpfung", position = 0)
        val funcap = FuncapSummary.of(
            listOf(form(2, today, 4), form(1, today.minusDays(30), 2)),
            today,
            listOf(fatigue),
            listOf(SymptomValue(today, 1, 2)),
        )
        val summary = DoctorSummary.of(TrackingRecords(berlin), today.minusDays(27), today, hints = emptyList(), funcap = funcap)
        val blocks = summaryBlocks(app.resources, summary, "", berlin, today)

        assertTrue(blocks.contains(SummaryBlock.Heading("Monatsfragebogen (FUNCAP55)")))
        val table = blocks.filterIsInstance<SummaryBlock.Table>().first { it.headers.first() == "Bereich" }
        assertEquals(listOf("Gesamtwert", "4", "2"), table.rows.first())
        assertEquals(9, table.rows.size)
        val means = blocks.filterIsInstance<SummaryBlock.Table>().last { it.rows.any { row -> row.first() == "Erschöpfung" } }
        assertEquals(listOf("Erschöpfung", "2", "1"), means.rows.single())
        assertTrue(blocks.any { it is SummaryBlock.Text && it.text == Funcap.SOURCE })
        pages(blocks)
    }
}
