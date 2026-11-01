package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Holds the seam between [Routines] and [OccurrenceLifecycle]: an occurrence's status is written, coupled ones covered
 * and goals settled in the lifecycle only, so that no way of changing a status can forget what hangs on it.
 */
class RoutinesSeamTest {

    @Test
    fun `the routines change statuses only through the occurrence lifecycle`() {
        val source = File("src/main/java/de/emilyshub/routines/Routines.kt").readText()

        val bypassing = BYPASSES.filter { it in source }

        assertEquals("Routines.kt is to go through OccurrenceLifecycle instead", emptyList<String>(), bypassing)
    }

    private companion object {
        val BYPASSES = listOf(
            "dao.setStatus(", "dao.setStatuses(", "dao.cover(", "dao.uncover(", "dao.uncoverAllBy(", "settleGoals(",
        )
    }
}
