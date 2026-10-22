package emilyshub.buildlogic

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property

interface ManifestPolicyExtension {
    /** Fully qualified name of the app's launcher activity, the only component allowed to be exported. */
    val launcherActivity: Property<String>
}

/**
 * Registers `verify<Variant>ManifestPolicy` for every app variant and runs it before
 * `assemble<Variant>` and `check`, so no APK is built from a manifest that breaks the policy.
 */
class ManifestPolicyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("manifestPolicy", ManifestPolicyExtension::class.java)
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
                    launcherActivity.set(extension.launcherActivity)
                    report.set(project.layout.buildDirectory.file("reports/manifest-policy/${variant.name}.txt"))
                }
                project.tasks.named { it == "assemble$variantName" || it == "check" }
                    .configureEach { dependsOn(verify) }
            }
        }
    }
}
