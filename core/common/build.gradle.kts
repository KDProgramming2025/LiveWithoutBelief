plugins {
    // Switch from android library to pure Kotlin JVM
    alias(libs.plugins.kotlin.jvm)
}

// Removed explicit jvmToolchain; will use current JDK

dependencies {
    // Only standard library (implicit) - no Android deps needed
}
