// newm on Caulis: the Kotlin port of newm (github.com/jbuchermn/newm, MIT).

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // The native bars are Compose.
    alias(libs.plugins.kotlin.compose)
    application
}

group = "io.github.aristarkhkenebas"
version = "0.1.0-SNAPSHOT"

val javaVersion = libs.versions.java.asProvider().get().toInt()
val bytecodeVersion = libs.versions.java.bytecode.get().toInt()

kotlin {
    jvmToolchain(javaVersion)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget(bytecodeVersion.toString())
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = bytecodeVersion
}

dependencies {
    implementation(libs.caulis.wm)
    implementation(libs.caulis.compose)
    // config.kts, compiled by newm itself.
    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "newm.MainKt"
    applicationName = "newm"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED", "-XX:+UseZGC")
}

// `newm --version` says it.
tasks.jar {
    manifest { attributes("Implementation-Version" to project.version) }
}
