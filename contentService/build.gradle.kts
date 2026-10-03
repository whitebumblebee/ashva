// SPDX-License-Identifier: Apache-2.0
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.json)
    implementation(libs.postgres.jdbc)
    implementation(libs.androidx.room.runtime)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.ktor.server.test.host)
}
application { mainClass.set("com.openinglab.service.MainKt") }
distributions.main {
    contents { into("notices") { from(rootProject.file("LICENSE"), rootProject.file("NOTICE"), rootProject.file("THIRD_PARTY_NOTICES.md")) } }
}
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
tasks.test { workingDir = rootProject.projectDir; exclude("**/*IntegrationTest*") }
tasks.register<Test>("integrationTest") {
    description = "Real isolated PostgreSQL and local HTTP/engine checks (explicit test-only environment required)"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/*IntegrationTest*")
    workingDir = rootProject.projectDir
    // Environment is intentionally not a task input/log value; fresh DB assertions must actually run.
    outputs.upToDateWhen { false }
}
