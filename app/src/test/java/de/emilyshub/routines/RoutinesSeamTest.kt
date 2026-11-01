package de.emilyshub.routines

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Holds the seam of [OccurrenceLifecycle]: an occurrence's status is written, coupled ones covered and given back, and a
 * routine's goal settled in the lifecycle only, so that no way of changing a status can forget what hangs on it.
 */
class RoutinesSeamTest {

    @Test
    fun `occurrence statuses are written only by the occurrence lifecycle`() {
        val bypassing = sources(MAIN).filterKeys { it != LIFECYCLE && it != DAO }
            .flatMap { (name, text) -> STATUS_WRITES.filter { it in text }.map { "$name: $it" } }

        assertEquals("To go through OccurrenceLifecycle instead", emptyList<String>(), bypassing)
    }

    @Test
    fun `the routines settle their goals only through the occurrence lifecycle`() {
        val bypassing = sources(File(MAIN, "de/emilyshub/routines")).filterKeys { it != LIFECYCLE }
            .filterValues { "watchingGoals(" in it || "settleGoals(" in it }.keys

        assertEquals("To go through OccurrenceLifecycle instead", emptySet<String>(), bypassing)
    }

    /** The Kotlin sources under [dir], by file name. */
    private fun sources(dir: File): Map<String, String> =
        dir.walk().filter { it.extension == "kt" }.associate { it.name to it.readText() }

    private companion object {
        val MAIN = File("src/main/java")
        const val LIFECYCLE = "OccurrenceLifecycle.kt"
        const val DAO = "RoutineDao.kt"
        val STATUS_WRITES = listOf(".setStatuses(", ".cover(", ".uncover(", ".uncoverAllBy(")
    }
}
