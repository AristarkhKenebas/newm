rootProject.name = "newm"

plugins {
    // Downloads the right JDK automatically if it is not installed.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

// Caulis comes from Maven Central (the version in gradle/libs.versions.toml). To build
// against its sources next to this folder (~/projects/caulis) instead — trying a change
// before it is released — run with -Pcaulis.local=true: Gradle then swaps
// io.github.aristarkhkenebas:* for those projects.
val caulisSources = file("../caulis")
if (providers.gradleProperty("caulis.local").orNull == "true" && caulisSources.resolve("caulis-core").isDirectory) {
    includeBuild(caulisSources)
}
