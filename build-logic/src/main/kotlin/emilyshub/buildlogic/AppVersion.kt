package emilyshub.buildlogic

/**
 * The version a build carries (SPEC.md §12): its [code], which Android compares and which must rise, its [name] as
 * people read it, and the short [commit] it was built from. In the CI each run of the workflow has its own, rising
 * number; a local build stays at the first.
 */
data class AppVersion(val code: Int, val name: String, val commit: String) {
    companion object {
        /** How many characters of the commit are shown, as Git shortens it. */
        private const val SHORT_COMMIT = 7

        /**
         * The version of a build from the workflow's [runNumber] (`GITHUB_RUN_NUMBER`) and the [commit] built
         * (`GITHUB_SHA`); without a run number, as in a local build, version 1, "0.1.0-lokal", commit "lokal".
         */
        fun of(runNumber: String?, commit: String?): AppVersion {
            val run = runNumber?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                ?: return AppVersion(code = 1, name = "0.1.0-lokal", commit = "lokal")
            val short = commit?.trim()?.take(SHORT_COMMIT)?.takeIf { it.isNotEmpty() } ?: "lokal"
            return AppVersion(code = run, name = "0.1.$run", commit = short)
        }
    }
}
