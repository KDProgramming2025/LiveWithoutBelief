plugins {
    // Pure Kotlin JVM for domain layer
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}
