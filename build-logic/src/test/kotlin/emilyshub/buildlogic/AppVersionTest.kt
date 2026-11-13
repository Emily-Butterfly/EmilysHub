package emilyshub.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every build of the CI carries its own, rising version (SPEC.md §12); a local build stays at the first. */
class AppVersionTest {

    @Test
    fun `in the CI the version comes from the run number and the short commit`() {
        val version = AppVersion.of(runNumber = "412", commit = "2dc38f5a9e0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d")

        assertEquals(AppVersion(code = 412, name = "0.1.412", commit = "2dc38f5"), version)
    }

    @Test
    fun `a local build stays at 1, named so`() {
        assertEquals(AppVersion(code = 1, name = "0.1.0-lokal", commit = "lokal"), AppVersion.of(runNumber = null, commit = null))
    }

    @Test
    fun `a run number that is no number counts as a local build`() {
        assertEquals(1, AppVersion.of(runNumber = "", commit = "").code)
        assertEquals("lokal", AppVersion.of(runNumber = "abc", commit = "").commit)
    }
}
