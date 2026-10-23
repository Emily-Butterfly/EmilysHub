package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class RoutineDraftTest {

    @Test
    fun `a routine needs a name`() {
        assertEquals(setOf(DraftProblem.NameMissing), draft(name = " \n ").problems)
    }

    @Test
    fun `the name is kept on one line and both texts lose surrounding spaces`() {
        val cleaned = draft(name = "  Zähne\n putzen\t", description = "\n Abends auch Zahnseide.\nNicht vergessen!  ").cleaned()

        assertEquals("Zähne putzen", cleaned.name)
        assertEquals("Abends auch Zahnseide.\nNicht vergessen!", cleaned.description)
    }

    @Test
    fun `a name fits up to 80 characters, not counting surrounding spaces`() {
        assertEquals(emptySet<DraftProblem>(), draft(name = "  " + "a".repeat(80) + "  ").problems)
        assertEquals(setOf(DraftProblem.NameTooLong), draft(name = "a".repeat(81)).problems)
    }

    @Test
    fun `a name of only invisible characters counts as missing`() {
        assertEquals(setOf(DraftProblem.NameMissing), draft(name = "​⁠‍").problems)
    }

    @Test
    fun `an emoji counts as one character`() {
        assertEquals(emptySet<DraftProblem>(), draft(name = "🌱".repeat(80)).problems)
        assertEquals(80, RoutineDraft.lengthOf("🌱".repeat(80)))
    }

    @Test
    fun `the description is optional and fits up to 1000 characters`() {
        assertEquals(emptySet<DraftProblem>(), draft(description = "").problems)
        assertEquals(emptySet<DraftProblem>(), draft(description = "a".repeat(1000)).problems)
        assertEquals(setOf(DraftProblem.DescriptionTooLong), draft(description = "a".repeat(1001)).problems)
    }

    private fun draft(name: String = "Zähne putzen", description: String = "") =
        RoutineDraft(name = name, description = description, time = LocalTime.of(8, 0))
}
