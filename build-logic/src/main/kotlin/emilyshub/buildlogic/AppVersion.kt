package emilyshub.buildlogic

/**
 * The version a build carries (SPEC.md §12): its [code], which Android compares and which must rise, its [name] as
 * people read it, and the short [commit] it was built from. In the CI each run of the workflow has its own, rising
 * number; a local build stays at the first.
 */
data class AppVersion(val code: Int, val name: String, val commit: String) {
    companion object {
        /**
         * Added to the workflow's run number, for the version and its name alike. It stays 0 as long as the workflow
         * keeps its file name; renamed, its runs count from 1 again, and this must be set above the last number
         * (docs/release-signierung.md).
         */
        private const val RUN_OFFSET = 0

        /** How many characters of the commit are shown, as Git shortens it. */
        private const val SHORT_COMMIT = 7

        private const val NAME_PREFIX = "0.1."
        private const val LOCAL = "lokal"

        /**
         * The version of a build from the workflow's [runNumber] (`GITHUB_RUN_NUMBER`) and the [commit] built
         * (`GITHUB_SHA`). Without a run number that is a positive number, as in a local build: version 1,
         * "0.1.0-lokal", commit "lokal"; a run without its commit says "unbekannt".
         */
        fun of(runNumber: String?, commit: String?): AppVersion {
            val run = runNumber?.trim()?.toIntOrNull()?.takeIf { it > 0 }
                ?: return AppVersion(code = 1, name = "${NAME_PREFIX}0-$LOCAL", commit = LOCAL)
            val code = run + RUN_OFFSET
            val short = commit?.trim()?.take(SHORT_COMMIT)?.takeIf { it.isNotEmpty() } ?: "unbekannt"
            return AppVersion(code = code, name = "$NAME_PREFIX$code", commit = short)
        }
    }
}
