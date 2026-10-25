// newm on Caulis: the Kotlin port of newm (github.com/jbuchermn/newm, MIT).

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
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
