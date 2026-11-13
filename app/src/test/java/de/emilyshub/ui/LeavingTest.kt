package de.emilyshub.ui

import de.emilyshub.languages.LessonDraft
import de.emilyshub.languages.TeacherDraft
import de.emilyshub.ledger.BookingDraft
import de.emilyshub.ledger.BookingKind
import de.emilyshub.ledger.PlanTyped
import de.emilyshub.ledger.RecurringDraft
import de.emilyshub.ledger.SubscriptionDraft
import de.emilyshub.ledger.TotalChoice
import de.emilyshub.todos.TodoDraft
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/** When leaving an editor asks first: only with a draft changed as it would be saved (Etappe 13, #87). */
class LeavingTest {

    @Test
    fun `spaces at the edges are no change`() {
        val saved = TodoDraft(title = "Milch kaufen", description = "Im Laden")

        assertFalse(changed(saved.copy(title = "  Milch kaufen ", description = "Im Laden  "), saved, TodoDraft::cleaned))
    }

    @Test
    fun `what would be saved otherwise is a change`() {
        val saved = TodoDraft(title = "Milch kaufen")

        assertTrue(changed(saved.copy(title = "Brot kaufen"), saved, TodoDraft::cleaned))
    }

    @Test
    fun `amounts and numbers typed with spaces around them are no change`() {
        val day = LocalDate.parse("2026-11-13")
        val booking = BookingDraft(BookingKind.Expense, amount = "12,50", date = day, categoryId = 1, description = "Brot")
        val recurring = RecurringDraft(BookingKind.Expense, amount = "9,99", description = "Netflix", firstMonth = YearMonth.of(2026, 11))
        val subscription = SubscriptionDraft(name = "Netflix", amount = "9,99", note = "Familie", notice = "1", renewal = "12", lead = "7")
        val lesson = LessonDraft(date = day, minutes = "45", price = "30", note = "Präpositionen")
        val teacher = TeacherDraft(name = "Samira", note = "dienstags")
        val plan = PlanTyped(budgets = mapOf(1L to "200"), choice = TotalChoice.Fixed, amount = "1500", saving = "0")

        assertFalse(changed(booking.copy(amount = " 12,50 ", description = "Brot "), booking, BookingDraft::cleaned))
        assertFalse(changed(recurring.copy(amount = "9,99 ", description = " Netflix"), recurring, RecurringDraft::cleaned))
        assertFalse(
            changed(
                subscription.copy(name = "Netflix ", amount = " 9,99", note = "Familie ", notice = " 1", renewal = "12 ", lead = " 7 "),
                subscription, SubscriptionDraft::cleaned,
            ),
        )
        assertFalse(changed(lesson.copy(minutes = "45 ", price = " 30", note = "Präpositionen  "), lesson, LessonDraft::cleaned))
        assertFalse(changed(teacher.copy(name = " Samira", note = "dienstags "), teacher, TeacherDraft::cleaned))
        assertFalse(changed(plan.copy(budgets = mapOf(1L to "200 "), amount = " 1500", saving = "0 "), plan, PlanTyped::cleaned))
    }
}
