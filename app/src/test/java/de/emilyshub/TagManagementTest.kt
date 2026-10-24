package de.emilyshub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The shared tags (SPEC.md §8), kept in the settings. */
@RunWith(AndroidJUnit4::class)
class TagManagementTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `tags are created, renamed and deleted in the settings, and deleting one asks first`() {
        launchApp().use {
            openTags()
            compose.onNodeWithText("Noch keine Tags.").assertIsDisplayed()

            compose.onNodeWithContentDescription("Tag anlegen").performClick()
            nameField().performTextInput("Arbeit")
            compose.onNodeWithText("Anlegen").performClick()

            compose.onNodeWithText("Arbeit").performClick()
            nameField().performTextReplacement("Beruf")
            compose.onNodeWithText("Umbenennen").performClick()

            compose.onNodeWithContentDescription("Beruf löschen").performClick()
            compose.onNodeWithText("Abbrechen").performClick()
            compose.onNodeWithText("Beruf").assertIsDisplayed()

            compose.onNodeWithContentDescription("Beruf löschen").performClick()
            compose.onNodeWithText("Löschen").performClick()
            compose.onNodeWithText("Beruf").assertDoesNotExist()
        }
        assertEquals(emptyList<String>(), runBlocking { app.tags.all().first().map { it.name } })
    }

    @Test
    fun `a tag cannot take the name of another, nor more than 30 characters`() {
        app.tag("Arbeit")

        launchApp().use {
            openTags()
            compose.onNodeWithContentDescription("Tag anlegen").performClick()

            nameField().performTextInput("arbeit")
            compose.onNodeWithText("Diesen Tag gibt es schon.").assertIsDisplayed()
            compose.onNodeWithText("Anlegen").assertIsNotEnabled()

            nameField().performTextReplacement("a".repeat(31))
            compose.onNodeWithText("Höchstens 30 Zeichen").assertIsDisplayed()
            compose.onNodeWithText("Anlegen").assertIsNotEnabled()
        }
        assertEquals(listOf("Arbeit"), runBlocking { app.tags.all().first().map { it.name } })
    }

    private fun openTags() {
        compose.onNodeWithContentDescription("Einstellungen").performClick()
        compose.onNodeWithText("Tags verwalten").performScrollTo().performClick()
    }

    private fun nameField() = compose.onNode(hasSetTextAction() and hasText("Name des Tags"))
}
