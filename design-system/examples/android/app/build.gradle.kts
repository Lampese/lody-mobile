plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// examples/android -> design-system
val designSystem = rootDir.resolve("../..")

android {
    // Generated design-system code imports app.lody.android.R, so the namespace
    // matches the real app; the application id keeps the preview installable alongside it.
    namespace = "app.lody.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lody.designpreview"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].java.srcDirs(
        designSystem.resolve("generated/android"),
        designSystem.resolve("runtime/android"),
    )
    sourceSets["main"].res.srcDir(designSystem.resolve("fonts"))
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
}
