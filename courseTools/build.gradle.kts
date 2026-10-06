// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Build-time deep-course tooling (docs/DEEP_COURSE_PLAN.md). Reuses shared legal rules; never shipped in the app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}
application {
    mainClass.set("com.openinglab.course.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx8g")
}
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir; maxHeapSize = "8g" }
tasks.test { workingDir = rootProject.projectDir; maxHeapSize = "2g" }
