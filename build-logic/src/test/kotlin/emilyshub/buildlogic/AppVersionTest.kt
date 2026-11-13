package emilyshub.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every build of the CI carries its own, rising version (SPEC.md §12); a local build stays at the first. */
class AppVersionTest {

    private val local = AppVersion(code = 1, name = "0.1.0-lokal", commit = "lokal")

    @Test
    fun `in the CI the version comes from the run number and the short commit`() {
        val version = AppVersion.of(runNumber = "412", commit = "2dc38f5a9e0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d")

        assertEquals(AppVersion(code = 412, name = "0.1.412", commit = "2dc38f5"), version)
    }

    @Test
    fun `a local build stays at 1, named so`() {
        assertEquals(local, AppVersion.of(runNumber = null, commit = null))
    }

    @Test
    fun `a run number that is no positive number counts as a local build`() {
        for (runNumber in listOf("", "abc", "0", "-3")) {
            assertEquals(runNumber, local, AppVersion.of(runNumber = runNumber, commit = "2dc38f5a9e0b"))
        }
    }

    @Test
    fun `a run without its commit says it does not know it`() {
        assertEquals(AppVersion(code = 412, name = "0.1.412", commit = "unbekannt"), AppVersion.of(runNumber = "412", commit = ""))
    }
}
