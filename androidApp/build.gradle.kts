import org.gradle.api.file.DirectoryProperty

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.openinglab.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.openinglab.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 18
        versionName = "0.17.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Process isolation prevents one long test JVM retaining every synthetic course/engine.
        // Do not clear package data: cold-restoration tests explicitly own their UUID databases.
        testInstrumentationRunnerArguments["clearPackageData"] = "false"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true // OS extracts the separate executable into its read-only native directory.
        jniLibs.keepDebugSymbols += "**/libstockfish.so" // Never rewrite the checksummed engine bytes.
    }
    sourceSets.getByName("main") {
        jniLibs.directories.add(rootProject.file(".engine-cache/prepared/jniLibs").path)
        assets.directories.add(rootProject.file(".engine-cache/prepared/assets").path)
    }
}

val verifyPreparedEngine = tasks.register<Exec>("verifyPreparedEngine") {
    workingDir(rootProject.projectDir)
    commandLine("node", "scripts/prepare-stockfish.mjs", "--verify")
}
tasks.named("preBuild") { dependsOn(verifyPreparedEngine) }

// Only reviewed immutable packs, not raw archives or every future content directory.
abstract class ReviewedContentAssets : Sync() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}
val packagedContent = tasks.register<ReviewedContentAssets>("packageReviewedContent") {
    from(rootProject.layout.projectDirectory.dir("content/packs")) {
        include("lichess-openings-c67912be581f-import-v1/**")
        include("lichess-broadcast-2020-04-2020-04-snap-import-v1/**")
        include("lichess-broadcast-2020-01-2020-01-snap-import-v1/**")
    }
    // Deep course packs: only the published, checksummed course file (docs/DEEP_COURSE_PLAN.md).
    from(rootProject.layout.projectDirectory.dir("content/courses")) {
        include("ruy-lopez/v1/course.json")
        into("courses")
    }
    outputDirectory.set(layout.buildDirectory.dir("generated/reviewedAssets"))
    into(outputDirectory.dir("content"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(packagedContent, ReviewedContentAssets::outputDirectory)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.kotlinx.serialization.json)
}
