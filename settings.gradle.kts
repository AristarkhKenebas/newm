rootProject.name = "newm"

plugins {
    // Downloads the right JDK automatically if it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // Caulis' own Maven repository on GitHub Pages: the same releases (same coordinates and
        // signatures), for a version not on Central yet (Central's free monthly limit). Only
        // Caulis is looked up there; Central stays first, so a version on either works.
        maven("https://aristarkhkenebas.github.io/caulis/maven") {
            content { includeGroup("io.github.aristarkhkenebas") }
        }
        google()
    }
}

// Caulis comes from Maven Central or its GitHub Pages mirror (the version in
// gradle/libs.versions.toml). To build
// against its sources next to this folder (~/projects/caulis) instead — trying a change
// before it is released — run with -Pcaulis.local=true: Gradle then swaps
// io.github.aristarkhkenebas:* for those projects.
val caulisSources = file("../caulis")
if (providers.gradleProperty("caulis.local").orNull == "true" && caulisSources.resolve("caulis-core").isDirectory) {
    includeBuild(caulisSources)
}
