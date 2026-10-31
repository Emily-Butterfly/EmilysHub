package de.emilyshub

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.emilyshub.ledger.BookingDraft
import de.emilyshub.ledger.BookingKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * The household book (SPEC.md §13), opened from the notes: bookings made and changed in its editor, each month with
 * its sums, its expenses by category and its bookings, and categories of its own.
 */
@RunWith(AndroidJUnit4::class)
class LedgerScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<TestApplication>()
    private val october = YearMonth.of(2026, 10)

    @Test
    fun `opened from the notes it shows this month, and back goes to the notes`() {
        launchApp().use {
            compose.onNodeWithContentDescription("Haushaltsbuch öffnen").assertDoesNotExist()
            compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Haushaltsbuch öffnen").performClick()

            compose.onNodeWithText("Haushaltsbuch").assertIsDisplayed()
            compose.onNodeWithText("Oktober 2026").assertIsDisplayed()
            compose.onNodeWithText("In diesem Monat gibt es noch keine Buchungen.").assertIsDisplayed()

            compose.onNodeWithContentDescription("Zurück").performClick()
            compose.onNodeWithContentDescription("Haushaltsbuch öffnen").assertIsDisplayed()
        }
    }

    @Test
    fun `an expense booked in the editor counts in the month, by its category, and is listed`() {
        launchApp().use {
            openLedger()
            compose.onNodeWithContentDescription("Buchung anlegen").performClick()
            field("Betrag in Euro").performTextInput("45,30")
            option("Lebensmittel").tap()
            field("Beschreibung (optional)").performScrollTo().performTextInput("Wocheneinkauf")
            compose.onNodeWithText("Speichern").performClick()

            ledgerList().performScrollToNode(hasText("Lebensmittel · 100 %"))
            compose.onNodeWithText("Lebensmittel · 100 %").assertIsDisplayed()
            ledgerList().performScrollToNode(hasText("Wocheneinkauf"))
            // The balance, and the booking.
            compose.onAllNodes(hasText("−45,30 €")).assertCountEquals(2)
        }
        val booking = runBlocking { app.ledger.month(october).first().bookings.single().booking }
        assertEquals(4_530L to LocalDate.parse("2026-10-23"), booking.amountCents to booking.date)
    }

    @Test
    fun `income needs an amount above zero and a category before it is saved`() {
        launchApp().use {
            openLedger()
            compose.onNodeWithContentDescription("Buchung anlegen").performClick()
            option("Einnahme").performClick()
            field("Betrag in Euro").performTextInput("0")
            compose.onNodeWithText("Der Betrag muss größer als 0 sein.").assertExists()
            compose.onNodeWithText("Speichern").performClick()
            compose.onNodeWithText("Bitte wähle eine Kategorie.").performScrollTo().assertIsDisplayed()

            field("Betrag in Euro").performScrollTo().performTextInput(",5")
            compose.onNodeWithText("0,50 €").assertExists()
        }
        assertEquals(emptyList<Any>(), runBlocking { app.ledger.month(october).first().bookings })
    }

    @Test
    fun `the months before and after have bookings of their own`() {
        book("Gehalt", BookingKind.Income, "2.500", "2026-09-30")
        book("Freizeit", BookingKind.Expense, "12", "2026-10-01")

        launchApp().use {
            openLedger()
            ledgerList().performScrollToNode(hasText("−12,00 €"))
            compose.onNodeWithText("−12,00 €").assertIsDisplayed()
            ledgerList().performScrollToNode(hasText("Oktober 2026"))
            compose.onNodeWithContentDescription("Voriger Monat").performClick()

            compose.onNodeWithText("September 2026").assertIsDisplayed()
            // Income and balance, and the booking, signed.
            compose.onAllNodes(hasText("2.500,00 €")).assertCountEquals(2)
            compose.onNodeWithText("+2.500,00 €").assertExists()
            compose.onNodeWithText("−12,00 €").assertDoesNotExist()
        }
    }

    @Test
    fun `a booking is opened, changed and deleted after asking`() {
        book("Freizeit", BookingKind.Expense, "8", "2026-10-20", "Kino")

        launchApp().use {
            openLedger()
            ledgerList().performScrollToNode(hasText("Kino"))
            compose.onNodeWithText("Kino").tap()
            compose.onNodeWithText("Buchung bearbeiten").assertIsDisplayed()
            compose.onNodeWithContentDescription("Löschen").performClick()
            compose.onNodeWithText("Die Buchung über 8,00 € wird gelöscht.").assertIsDisplayed()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Kino").assertDoesNotExist()
            compose.onNodeWithText("In diesem Monat gibt es noch keine Buchungen.").assertExists()
        }
    }

    @Test
    fun `a category deleted after asking takes its bookings to "Sonstiges", which stays`() {
        book("Kleidung", BookingKind.Expense, "39,99", "2026-10-12")

        launchApp().use {
            openLedger()
            compose.onNodeWithContentDescription("Kategorien").performClick()
            compose.onNodeWithContentDescription("„Kleidung“ löschen").performClick()
            compose.onNodeWithText("„Kleidung“ hat eine Buchung. Sie wandert nach „Sonstiges“.").assertIsDisplayed()
            compose.onNode(hasText("Löschen") and hasAnyAncestor(isDialog())).performClick()

            compose.onNodeWithText("Kleidung").assertDoesNotExist()
            compose.onNodeWithContentDescription("„Sonstiges“ löschen").assertDoesNotExist()
        }
        val booking = runBlocking { app.ledger.month(october).first().bookings.single() }
        assertEquals("Sonstiges", booking.category.name)
    }

    @Test
    fun `a category is added and renamed`() {
        launchApp().use {
            openLedger()
            compose.onNodeWithContentDescription("Kategorien").performClick()
            ledgerList().performScrollToNode(hasText("Kategorie anlegen"))
            compose.onAllNodes(hasText("Kategorie anlegen") and hasClickAction())[0].performClick()
            field("Name").performTextInput("Reisen")
            compose.onNode(hasText("Anlegen") and hasAnyAncestor(isDialog())).performClick()

            // Under the top bar, where a touch would not reach it.
            compose.onNodeWithText("Reisen").performSemanticsAction(SemanticsActions.OnClick)
            field("Name").performTextReplacement("Reisen und Urlaub")
            compose.onNode(hasText("Umbenennen") and hasAnyAncestor(isDialog())).performClick()
            compose.onNodeWithText("Reisen und Urlaub").assertExists()
        }
    }

    private fun openLedger() {
        compose.onNode(hasText("Notizen") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Haushaltsbuch öffnen").performClick()
    }

    private fun book(category: String, kind: BookingKind, amount: String, date: String, description: String = "") = runBlocking {
        val id = app.ledger.categories().first().single { it.kind == kind && it.name == category }.id
        app.ledger.create(BookingDraft(kind, amount, LocalDate.parse(date), id, description))
    }

    private fun ledgerList() = compose.onNode(hasScrollAction())

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun option(label: String) = compose.onNode(isSelectable() and hasText(label))

    /** Scrolls the editor to the node first: its form is longer than the test screen. */
    private fun SemanticsNodeInteraction.tap() = performScrollTo().performClick()
}
