pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EmilysHub"
include(":app")

/**
 * The lowest version of a library the build may resolve, for known vulnerabilities (Dependabot alerts,
 * docs/agents/dependabot.md): libraries no build file asks for itself, brought in by others, the app's (Guava, through
 * Health Connect) and the build's own (the Android Gradle plugin, lint, KSP). One asked for in a lower version is
 * resolved in this one; Guava keeps its flavour (-jre, -android), -jre if none was asked for, as Guava has no version
 * without one (its Gradle metadata picks the Android variant for the app all the same). Remove a line once nothing
 * resolves below it any more.
 */
val securityFloors = mapOf(
    // GHSA-xxph-c9ww-hj94: deserialization of CompactHashMap and others exhausts memory.
    "com.google.guava:guava" to "33.7.2",
    // GHSA-j288-q9x7-2f5v: uncontrolled recursion on long inputs.
    "org.apache.commons:commons-lang3" to "3.18.0",
    // GHSA-7r82-7xv7-xcpj: cross-site scripting through the request URI.
    "org.apache.httpcomponents:httpclient" to "4.5.14",
    // GHSA-3677-xxcr-wjqv: denial of service by compressed JWE content.
    "org.bitbucket.b_c:jose4j" to "0.9.6",
    // GHSA-9pwp-9qqc-pr26, GHSA-qp49-qgx5-5m26, GHSA-wg6q-6289-32hp; the three in step, as they are released together.
    "org.bouncycastle:bcprov-jdk18on" to "1.85",
    "org.bouncycastle:bcpkix-jdk18on" to "1.85",
    "org.bouncycastle:bcutil-jdk18on" to "1.85",
    // GHSA-2363-cqg2-863c: XML external entities.
    "org.jdom:jdom2" to "2.0.6.1",
)

/** The numbers of [version] before any suffix such as "-jre", to compare by. */
fun numbersOf(version: String): List<Int> =
    version.substringBefore('-').split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

/** Whether [version] is lower than [floor], number by number. */
fun isBelow(version: String, floor: String): Boolean {
    val (have, need) = numbersOf(version) to numbersOf(floor)
    for (i in 0 until maxOf(have.size, need.size)) {
        val compared = have.getOrElse(i) { 0 }.compareTo(need.getOrElse(i) { 0 })
        if (compared != 0) return compared < 0
    }
    return false
}

/** Raises what [configurations] resolve to the [securityFloors]. */
fun raiseToFloors(configurations: ConfigurationContainer) = configurations.configureEach {
    resolutionStrategy.eachDependency {
        val floor = securityFloors["${requested.group}:${requested.name}"] ?: return@eachDependency
        val version = requested.version ?: return@eachDependency
        if (isBelow(version, floor)) {
            val asked = version.substringAfter('-', "")
            val flavour = when {
                asked.isNotEmpty() -> "-$asked"
                requested.group == "com.google.guava" -> "-jre"
                else -> ""
            }
            useVersion(floor + flavour)
            because("known vulnerability below $floor (settings.gradle.kts, securityFloors)")
        }
    }
}

gradle.allprojects {
    raiseToFloors(buildscript.configurations)
    raiseToFloors(configurations)
}
