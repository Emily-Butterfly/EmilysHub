package de.emilyshub.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** The area once called "Wunschliste" speaks of goals everywhere (SPEC.md §6): no text of the app says "Wunsch" any more. */
class GoalsWordingTest {

    @Test
    fun `no text of the app speaks of wishes`() {
        val wishes = Regex("""W[uü]nsch""", RegexOption.IGNORE_CASE)
        val found = File("src/main/res").walk().filter { it.extension == "xml" }
            .flatMap { file -> file.readLines().filter(wishes::containsMatchIn).map { "${file.name}: ${it.trim()}" } }
            .toList()
        assertEquals(emptyList<String>(), found)
    }
}
