package de.emilyshub.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The area once called "Wunschliste" speaks of goals everywhere (SPEC.md §6): no text of the app says "Wunsch" any more,
 * neither in its resources nor in a string written into the code.
 */
class GoalsWordingTest {

    private val wishes = Regex("""W[uü]nsch""", RegexOption.IGNORE_CASE)

    @Test
    fun `no text in the resources speaks of wishes`() {
        val files = sources("src/main/res", "xml")
        val found = files.flatMap { file -> file.readLines().filter(wishes::containsMatchIn).map { "${file.name}: ${it.trim()}" } }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `no string written into the code speaks of wishes`() {
        val strings = Regex(""""[^"\n]*"""")
        val found = sources("src/main/java", "kt").flatMap { file ->
            file.readLines()
                .filterNot { it.trimStart().let { line -> line.startsWith("*") || line.startsWith("/*") || line.startsWith("//") } }
                .flatMap { line -> strings.findAll(line).map { it.value }.filter(wishes::containsMatchIn).toList() }
                .map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), found)
    }

    /** The files of [extension] below [directory] of the app module, which must be there. */
    private fun sources(directory: String, extension: String): List<File> {
        val root = File(directory)
        assertTrue("$root is where the test looks", root.isDirectory)
        return root.walk().filter { it.extension == extension }.toList().also { assertTrue("Files found in $root", it.isNotEmpty()) }
    }
}
