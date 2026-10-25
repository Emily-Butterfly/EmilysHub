plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    id("emilyshub.manifest-policy")
}

android {
    namespace = "de.emilyshub"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.emilyshub"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Only CI sets these, from the repository secrets (docs/release-signierung.md).
        // Everywhere else the release APK stays unsigned.
        val keystore = providers.environmentVariable("RELEASE_KEYSTORE")
        if (keystore.isPresent) {
            create("release") {
                storeFile = file(keystore.get())
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // The app is German only; drop other languages that libraries bring along.
        localeFilters += "de"
    }

    dependenciesInfo {
        // Dependency metadata is only useful to Google Play.
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        // targetSdk 35 is fixed by the spec; version nags would make CI fail on every new release.
        disable += setOf("OldTargetApi", "GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val schemas = "$projectDir/schemas"

room {
    // Every schema version is checked in, so migrations can be written and tested against it.
    schemaDirectory(schemas)
}

androidComponents {
    onVariants { variant ->
        // A backup of an older app version is brought up to date in a database laid out as the app's was then.
        variant.sources.assets?.addStaticSourceDirectory(schemas)
    }
}

manifestPolicy {
    launcherActivity = "de.emilyshub.MainActivity"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.sqlcipher.android)
    ksp(libs.androidx.room.compiler)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
