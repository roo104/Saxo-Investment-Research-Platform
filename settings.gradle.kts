plugins {
    // Lets Gradle auto-provision the JDK 25 toolchain declared in build.gradle.kts
    // if it is not already installed locally.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

rootProject.name = "saxo-investment-manager"
