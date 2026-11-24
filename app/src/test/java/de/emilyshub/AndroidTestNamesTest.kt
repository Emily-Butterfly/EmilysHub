package de.emilyshub

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The names of the instrumented tests fit the dex format: a name in backticks may hold only what dex allows in a simple
 * name, or the job "instrumented" fails at :app:dexBuilderDebugAndroidTest before any test runs, as it did three times
 * (a comma twice, an apostrophe once). Checked here, where the unit tests run on every build, against what dex allows
 * rather than what it refuses, so that no character it refuses is left out.
 */
class AndroidTestNamesTest {

    @Test
    fun `no instrumented test's name holds a character dex cannot carry`() {
        val sources = File("src/androidTest").walkTopDown().filter { it.extension == "kt" }.toList()
        check(sources.isNotEmpty()) { "The instrumented tests are not where they were looked for" }
        val refused = sources.flatMap { file ->
            Regex("fun `([^`]*)`").findAll(file.readText()).map { it.groupValues[1] }
                .filter { name -> !name.all(::dexAllows) }
                .map { "${file.name}: $it" }
        }
        assertEquals(emptyList<String>(), refused)
    }

    @Test
    fun `the check knows what dex allows and what it refuses`() {
        assertEquals(true, "version 63 moves to version 64 with the reasons as Standard-like_\$1 ä€".all(::dexAllows))
        assertEquals(listOf(false, false, false, false, false), listOf("one's", "a, b", "a.b", "(x)", "a:b").map { it.all(::dexAllows) })
    }

    private companion object {
        /**
         * Whether [char] may stand in a simple name in dex (format 040, Android 11 on, as the app needs): ASCII letters,
         * digits, the space, "$", "-" and "_", and of the rest of Unicode what the format lists.
         */
        fun dexAllows(char: Char): Boolean = when (char) {
            in 'A'..'Z', in 'a'..'z', in '0'..'9', ' ', '$', '-', '_' -> true
            '\u00a0', in '\u00a1'..'\u1fff', in '\u2000'..'\u200a', in '\u2010'..'\u2027', '\u202f',
            in '\u2030'..'\ud7ff', in '\ue000'..'\uffef' -> true
            // The halves of a character beyond the first plane, which dex allows whole.
            in '\ud800'..'\udfff' -> true
            else -> false
        }
    }
}
