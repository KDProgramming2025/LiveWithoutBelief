plugins {
    // Switch from android library to pure Kotlin JVM
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Removed explicit jvmToolchain to avoid requiring local JDK 17
// Rely on current JDK; can reintroduce when JDK 17 installed

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
