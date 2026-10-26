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
    implementation(libs.caulis.screenshot)
    // config.lua.
    implementation(libs.luaj)
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

// The templates (config.kts and the rest, newm's wallpaper) go into newm itself: newm writes
// them for a user who has none, and `newm install-session` into /etc/newm.
tasks.processResources {
    from("templates") { into("newm/templates") }
}

// The `newm` script finds a Java new enough by itself: JAVA_HOME or the `java` on PATH when
// they are, else the newest under /usr/lib/jvm (Arch's default Java may be older than newm's).
// With none, it says what to install instead of Java's own error.
tasks.startScripts {
    val needed = bytecodeVersion
    doLast {
        val find = """
            |# newm needs Java $needed or newer: found here when JAVA_HOME and `java` are older.
            |newm_java_version() { sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "${'$'}1/release" 2>/dev/null; }
            |newm_java_ok() { v=${'$'}(newm_java_version "${'$'}1"); [ -n "${'$'}v" ] && [ "${'$'}v" -ge $needed ]; }
            |if [ -n "${'$'}JAVA_HOME" ] && newm_java_ok "${'$'}JAVA_HOME"; then
            |    :
            |elif [ -z "${'$'}JAVA_HOME" ] && command -v java >/dev/null 2>&1 && newm_java_ok "${'$'}(dirname "${'$'}(dirname "${'$'}(readlink -f "${'$'}(command -v java)")")")"; then
            |    :
            |else
            |    JAVA_HOME=
            |    for newm_jvm in ${'$'}(ls -d /usr/lib/jvm/*/ 2>/dev/null | sort -rV); do
            |        if newm_java_ok "${'$'}{newm_jvm%/}"; then JAVA_HOME=${'$'}{newm_jvm%/}; break; fi
            |    done
            |    if [ -z "${'$'}JAVA_HOME" ]; then
            |        echo "newm needs Java $needed or newer (on Arch: sudo pacman -S jdk-openjdk)." >&2
            |        exit 1
            |    fi
            |    export JAVA_HOME
            |fi
            |
            """.trimMargin()
        val script = unixScript
        val text = script.readText()
        // Right after the license header, before the script looks for java.
        val at = text.indexOf("\n# Attempt to set APP_HOME")
        check(at > 0) { "the start script changed: newm's Java finding is not in it" }
        script.writeText(text.substring(0, at + 1) + find + text.substring(at + 1))
    }
}
