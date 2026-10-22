plugins {
    alias(libs.plugins.android.application) apply false
    // AGP's built-in Kotlin support uses the Kotlin Gradle plugin on this classpath,
    // so pinning it here keeps the compiler and the Compose compiler plugin in sync.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
