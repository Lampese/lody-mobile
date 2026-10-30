plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.lody.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lody.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true; buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.webkit)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.process)
    implementation(libs.lifecycle.runtime)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.json.test)
    androidTestImplementation(libs.test.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    debugImplementation(libs.activity.compose)
    debugImplementation(libs.compose.material3)
    debugImplementation(libs.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.test.junit)
}

abstract class RuntimeAssets : Exec() {
    @get:OutputDirectory abstract val mainAssets: DirectoryProperty
    @get:OutputDirectory abstract val debugAssets: DirectoryProperty
}
val generateRuntimeAssets = tasks.register<RuntimeAssets>("generateRuntimeAssets") {
    workingDir(rootProject.file("runtime"))
    commandLine("node", "build.mjs")
    inputs.dir(rootProject.file("runtime/vendor"))
    inputs.dir(rootProject.file("runtime/src"))
    inputs.file(rootProject.file("runtime/build.mjs"))
    inputs.file(rootProject.file("runtime/snapshot.mjs"))
    inputs.file(rootProject.file("runtime/package-lock.json"))
    mainAssets.set(layout.buildDirectory.dir("generated/runtime/main"))
    debugAssets.set(layout.buildDirectory.dir("generated/runtime/debug"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateRuntimeAssets) { it.mainAssets }
    if (variant.buildType == "debug") {
        variant.sources.assets?.addGeneratedSourceDirectory(generateRuntimeAssets) { it.debugAssets }
    }
}

tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("runtime/contract/fixtures.json"))
    systemProperty("runtime.fixtures", rootProject.file("runtime/contract/fixtures.json").absolutePath)
}
