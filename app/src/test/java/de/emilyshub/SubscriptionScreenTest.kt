package de.emilyshub

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.ledger.BookingInterval
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.RecurringDraft
import de.emilyshub.ledger.SubscriptionEditing
import de.emilyshub.ledger.SubscriptionStatus
import de.emilyshub.todos.RecurrenceEnd
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * The Abos of the household book (SPEC.md §13): made in their list, made from a fixed booking ("Als Abo führen"), and
 * cancelled, the fixed booking ending with them if wanted.
 */
@RunWith(AndroidJUnit4::class)
class SubscriptionScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()

    @Test
    fun `an Abo made in its list shows its last day, the days to it, and what it costs a month`() {
        launchApp().use {
            openSubscriptions()
            compose.onNodeWithText("Noch keine Abos", substring = true).assertIsDisplayed()
            compose.onNodeWithContentDescription("Abo anlegen").performClick()
            field("Name").performTextInput("Streaming")
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Bitte wähle ein Datum.").performScrollTo().assertIsDisplayed()

            field("Betrag in Euro (optional)").performScrollTo().performTextInput("12,99")
            compose.onNode(hasText("Kündigen bis") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(hasText("27", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Laufende Abos: 12,99 € im Monat").assertIsDisplayed()
            compose.onNode(hasText("Streaming") and hasText("Kündigen bis 27.10.2026 · noch 4 Tage")).assertIsDisplayed()
        }
    }

    @Test
    fun `a fixed booking is kept as an Abo, which has its name and amount, and opens from it then`() {
        val streaming = template("Streaming", "12,99")
        launchApp().use {
            openLedger()
            compose.onNodeWithContentDescription("Fixbuchungen").performClick()
            compose.onNodeWithText("Streaming").performClick()
            compose.onNodeWithText("Als Abo führen").performScrollTo().performClick()

            field("Name").assert(hasText("Streaming"))
            option("Streaming").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Betrag und Abstand kommen von der Fixbuchung: 12,99 € Monatlich").performScrollTo().assertIsDisplayed()
            compose.onNode(hasText("Kündigen bis") and hasClickAction()).performScrollTo().performClick()
            compose.onNode(hasText("30", substring = true) and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Speichern").performClick()

            compose.onNodeWithText("Abo öffnen").performScrollTo().assertIsDisplayed()
        }
        val abo = runBlocking { app.subscriptions.overview().first().running.single() }
        assertEquals(streaming, abo.subscription.recurringId)
        assertEquals(SubscriptionStatus.Due(LocalDate.parse("2026-10-30"), daysLeft = 7), abo.status)
    }

    @Test
    fun `cancelled, an Abo moves to the cancelled ones, and its fixed booking ends then if wanted`() {
        val streaming = template("Streaming", "12,99")
        runBlocking {
            val draft = checkNotNull(app.subscriptions.draftFor(streaming)).copy(cancelBy = LocalDate.parse("2026-10-30"))
            app.subscriptions.save(SubscriptionEditing.New, draft)
        }
        launchApp().use {
            openSubscriptions()
            compose.onNodeWithText("Streaming").performClick()
            compose.onNodeWithText("Gekündigt").performClick()
            compose.onNode(hasText("Datum ändern") and hasAnyAncestor(isDialog())).performClick()
            // The date of the deadline, chosen as it is.
            compose.onNode(hasText("OK") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Soll die verknüpfte Fixbuchung „Streaming“ am 30.10.2026 enden?").assertIsDisplayed()
            compose.onNodeWithText("Beenden").performClick()

            compose.onNodeWithText("Gekündigt").assertIsDisplayed()
            compose.onNodeWithText("Endet am 30.10.2026").assertIsDisplayed()
            compose.onNodeWithText("Laufende Abos: 0,00 € im Monat").assertIsDisplayed()
        }
        assertEquals(
            RecurrenceEnd.OnDate(LocalDate.parse("2026-10-30")),
            runBlocking { app.ledger.editingTemplate(streaming)?.original?.end },
        )
    }

    private fun openLedger() {
        compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Haushaltsbuch öffnen").performClick()
    }

    private fun openSubscriptions() {
        openLedger()
        compose.onNodeWithContentDescription("Abos").performClick()
    }

    private fun template(description: String, amount: String): Long = runBlocking {
        val leisure = app.ledger.categories().first().single { it.kind == BookingKind.Expense && it.name == "Freizeit" }.id
        app.ledger.createTemplate(
            RecurringDraft(BookingKind.Expense, amount, leisure, description, BookingInterval.Monthly, day = 1, firstMonth = YearMonth.of(2026, 10)),
        )
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))
}
