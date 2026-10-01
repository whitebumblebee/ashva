import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
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
application { mainClass.set("com.openinglab.content.MainKt") }
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }
tasks.test { workingDir = rootProject.projectDir }
