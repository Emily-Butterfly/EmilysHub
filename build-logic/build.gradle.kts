plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.android.gradle.api)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        register("manifestPolicy") {
            id = "emilyshub.manifest-policy"
            implementationClass = "emilyshub.buildlogic.ManifestPolicyPlugin"
        }
    }
}
