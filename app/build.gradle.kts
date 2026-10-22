plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
    }

    buildTypes {
        release {
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
