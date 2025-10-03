plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // JVM-only module; no Android dependencies to keep variant resolution simple.
}
