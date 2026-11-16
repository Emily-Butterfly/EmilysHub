package de.emilyshub

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The names of the instrumented tests fit the dex format: a name in backticks may hold no comma (nor any of the other
 * characters dex refuses), or the job "instrumented" fails at :app:dexBuilderDebugAndroidTest before any test runs, as
 * it did twice. Checked here, where the unit tests run on every build.
 */
class AndroidTestNamesTest {

    @Test
    fun `no instrumented test's name holds a character dex cannot carry`() {
        val sources = File("src/androidTest").walkTopDown().filter { it.extension == "kt" }.toList()
        check(sources.isNotEmpty()) { "The instrumented tests are not where they were looked for" }
        val refused = sources.flatMap { file ->
            Regex("fun `([^`]*)`").findAll(file.readText()).map { it.groupValues[1] }
                .filter { name -> name.any { it in DEX_REFUSES } }
                .map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), refused)
    }

    private companion object {
        /** What a method's name in dex cannot hold, of what a Kotlin name in backticks can. */
        const val DEX_REFUSES = ",.;[]/<>"
    }
}
