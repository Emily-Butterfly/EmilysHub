package emilyshub.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Registers `verify<Variant>ManifestPolicy` for every app variant and runs it before
 * `assemble<Variant>` and `check`, so no APK is built from a manifest that breaks the policy.
 */
class ManifestPolicyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            androidComponents.onVariants { variant ->
                val variantName = variant.name.replaceFirstChar(Char::uppercase)
                val verify = project.tasks.register(
                    "verify${variantName}ManifestPolicy",
                    VerifyManifestPolicyTask::class.java,
                ) {
                    group = "verification"
                    description = "Prüft das gemergte $variantName-Manifest gegen die Sicherheitsregeln."
                    mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                    report.set(project.layout.buildDirectory.file("reports/manifest-policy/${variant.name}.txt"))
                }
                project.tasks.named { it == "assemble$variantName" || it == "check" }
                    .configureEach { dependsOn(verify) }
            }
        }
    }
}
