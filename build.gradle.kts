// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.paparazzi) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    // Apply code style & static analysis plugins to all subprojects
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            android.set(true)
            ignoreFailures.set(false)
        }
    }
    plugins.withId("io.gitlab.arturbosch.detekt") {
        extensions.configure(io.gitlab.arturbosch.detekt.extensions.DetektExtension::class.java) {
            buildUponDefaultConfig = true
            allRules = false
            config.from(files(rootProject.file("detekt.yml")))
        }
        tasks.withType<io.gitlab.arturbosch.detekt.Detekt> { jvmTarget = "17" }
    }
}
