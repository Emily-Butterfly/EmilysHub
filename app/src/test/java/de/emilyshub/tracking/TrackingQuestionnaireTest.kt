package de.emilyshub.tracking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** The monthly questionnaire (SPEC.md §17, "Monatsfragebogen"): the draft, completing it, when it is due, its results. */
@RunWith(AndroidJUnit4::class)
class TrackingQuestionnaireTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val fixture = TrackingFixture(context, now = "2026-11-16T10:00:00Z")
    private val questionnaire = fixture.questionnaire

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `due at first, a draft kept as answered, completed today and then not due`() = runBlocking {
        val first = questionnaire.overview()!!
        assertTrue(first.due)
        assertNull(first.draft)

        val draft = questionnaire.begin()!!
        assertEquals(LocalDate.parse("2026-11-16"), draft.form.startedOn)
        assertTrue(questionnaire.answer(draft.form.id, 1, 6))
        assertTrue(questionnaire.answer(draft.form.id, 2, 4))
        assertTrue(questionnaire.answer(draft.form.id, 2, null))
        // Begun again, it is the same draft with its answers.
        assertEquals(mapOf(1 to 6), questionnaire.begin()!!.answers)

        fixture.at("2026-11-18T10:00:00Z")
        assertTrue(questionnaire.complete(draft.form.id))
        val after = questionnaire.overview()!!
        assertFalse(after.due)
        assertNull(after.draft)
        val result = after.results.single()
        assertEquals(LocalDate.parse("2026-11-18"), result.day)
        assertEquals(6.0, result.score.domains.getValue('A')!!, 1e-9)

        fixture.at("2026-12-16T10:00:00Z")
        assertTrue(questionnaire.overview()!!.due)
    }

    @Test
    fun `answers off the scale, of no item or to a completed one are refused`() = runBlocking {
        val id = questionnaire.begin()!!.form.id
        assertFalse(questionnaire.answer(id, 1, 7))
        assertFalse(questionnaire.answer(id, 56, 3))
        assertTrue(questionnaire.answer(id, 55, 0))
        assertTrue(questionnaire.complete(id))
        assertFalse(questionnaire.answer(id, 1, 3))
        assertFalse(questionnaire.complete(id))
    }

    @Test
    fun `one without any answer is not completed`() = runBlocking {
        val id = questionnaire.begin()!!.form.id
        assertFalse(questionnaire.complete(id))
        assertNotNull(questionnaire.overview()!!.draft)
    }

    @Test
    fun `a draft older than 7 days is stale, cannot be completed and goes once deleted`() = runBlocking {
        val id = questionnaire.begin()!!.form.id
        questionnaire.answer(id, 1, 3)
        fixture.at("2026-11-23T10:00:00Z")
        assertFalse(questionnaire.overview()!!.draft!!.stale)
        fixture.at("2026-11-24T10:00:00Z")
        assertTrue(questionnaire.overview()!!.draft!!.stale)
        assertFalse(questionnaire.complete(id))

        questionnaire.delete(id)
        assertNull(questionnaire.overview()!!.draft)
        assertTrue(questionnaire.begin()!!.form.id != id)
    }

    @Test
    fun `a result with its answers, and the history's points`() = runBlocking {
        val id = questionnaire.begin()!!.form.id
        Funcap.DOMAINS.forEach { questionnaire.answer(id, it.items.first().number, 3) }
        questionnaire.complete(id)

        val (result, answers) = questionnaire.result(id)!!
        assertEquals(8, answers.size)
        assertEquals(3.0, result.score.total!!, 1e-9)

        val history = fixture.evaluation.history(HistoryRange.Four)!!
        val total = history.series.single { it.kind == SeriesKind.FuncapTotal }
        assertEquals(3.0, total.points.single { it.first == LocalDate.parse("2026-11-16") }.second!!, 1e-9)
        assertEquals(1, total.points.count { it.second != null })
        assertEquals(8, history.series.count { it.kind is SeriesKind.FuncapDomain })
    }
}
