package de.emilyshub.ui.tracking

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.tracking.Funcap
import de.emilyshub.tracking.TrackingFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/** The monthly questionnaire's page (SPEC.md §17): begun, answered, left and taken up again, completed. */
@RunWith(AndroidJUnit4::class)
class QuestionnairePageTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val fixture = TrackingFixture(ApplicationProvider.getApplicationContext<Context>(), now = "2026-11-16T10:00:00Z")

    @After
    fun tearDown() = fixture.close()

    @Test
    fun `begun, answered, left and taken up again, then completed`() {
        var shown by mutableStateOf(true)
        // Robolectric builds an activity the manifest does not name, where ActivityScenario would not.
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().setContent {
            if (shown) QuestionnairePage(fixture.tracking, fixture.questionnaire, PaddingValues(), onOpen = {}, onBack = {})
        }
        compose.onNodeWithText("Beginnen").performClick()
        compose.waitForIdle()
        val unproblematic = "6: ${Funcap.ANSWERS[6]}"
        compose.onAllNodesWithContentDescription(unproblematic).onFirst().performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 von 55 Fragen beantwortet").performScrollTo()

        // Left and opened again: the draft is there with its answer.
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        compose.onNodeWithText("1 von 55 Fragen beantwortet").performScrollTo()
        assertEquals(mapOf(1 to 6), runBlocking { fixture.questionnaire.overview()!!.draft!!.answers })

        compose.onNodeWithText("Abschließen").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Abschließen").onLast().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ausgefüllt").performScrollTo()
        compose.onNodeWithText("16.11.2026").performScrollTo()
        val overview = runBlocking { fixture.questionnaire.overview()!! }
        assertNull(overview.draft)
        assertEquals(6.0, overview.results.single().score.domains.getValue('A')!!, 1e-9)
    }
}
