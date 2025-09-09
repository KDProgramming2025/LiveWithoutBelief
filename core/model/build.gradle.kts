plugins {
    // Switch from android library to pure Kotlin JVM
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Align on JDK 17 across JVM modules for test-fixtures compatibility
kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
