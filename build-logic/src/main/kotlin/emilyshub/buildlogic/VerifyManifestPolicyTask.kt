package emilyshub.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Fails the build when the merged manifest breaks [manifestPolicyViolations]. */
@CacheableTask
abstract class VerifyManifestPolicyTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val launcherActivity: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val violations = manifestPolicyViolations(mergedManifest.get().asFile.readText(), launcherActivity.get())
        val lines = violations.map { " - ${it.message()}" }
        report.get().asFile.writeText(if (lines.isEmpty()) "OK\n" else lines.joinToString("\n", postfix = "\n"))
        if (lines.isNotEmpty()) {
            throw GradleException(
                "Das gemergte Manifest verletzt die Sicherheitsregeln (SPEC.md §9):\n" +
                    lines.joinToString("\n") +
                    "\nHerkunft der Einträge: build/outputs/logs/manifest-merger-*-report.txt",
            )
        }
    }
}

private fun ManifestViolation.message() = when (this) {
    is ManifestViolation.ForbiddenPermission ->
        "Verbotene Berechtigung $permission – die App darf nicht ins Internet."
    is ManifestViolation.ExportedComponent ->
        "<$kind> $name ist exportiert – nur die eigene Launcher-Activity darf exportiert sein."
    ManifestViolation.BackupAllowed ->
        "android:allowBackup muss \"false\" sein."
    ManifestViolation.MissingDataExtractionRules ->
        "android:dataExtractionRules fehlt – Cloud-Backup und Gerätetransfer müssen ausgeschlossen sein."
}
