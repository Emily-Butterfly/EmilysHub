package de.emilyshub.wishes

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Holds the seam of [GoalWatch]: what changes parts of goals outside the goals names them to the watch, and never holds
 * on to how a part was, so that no change can forget to settle a goal or settle it from a part taken at the wrong time.
 */
class GoalSeamTest {

    @Test
    fun `outside the goals, nothing holds a part of a goal or settles one itself`() {
        val main = File("src/main/java")
        val goals = File(main, "de/emilyshub/wishes")
        val outside = main.walk()
            .filter { it.extension == "kt" && !it.startsWith(goals) }
            .filter { file -> file.readText().let { "GoalPart" in it || "settleGoals(" in it } }
            .map { it.name }
            .toList()

        assertEquals("To name the part to GoalWatch instead", emptyList<String>(), outside)
    }
}
