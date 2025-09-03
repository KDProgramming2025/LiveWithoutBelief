// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.hilt.android) apply false
    
    alias(libs.plugins.paparazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.6"
    id("org.jetbrains.kotlinx.kover") version "0.9.0"
}

subprojects {
    // Code style handled manually
}

detekt {
    config.setFrom(files(rootProject.file("detekt.yml")))
    buildUponDefaultConfig = true
    autoCorrect = false
    parallel = true
}

kover {
    reports {
        verify {
            rule("OverallLineCoverage") {
                bound { minValue = 70 } // baseline; raise per module later
            }
        }
    }
}

tasks.register("quality") {
    group = "verification"
    description = "Runs static analysis and tests with coverage verification"
    // Collect all test tasks from subprojects (unit tests)
    val testTasks = subprojects.flatMap { sp ->
        sp.tasks.matching { it.name.contains("test", ignoreCase = true) && it.name.endsWith("UnitTest") }
    }
    dependsOn(testTasks)
    dependsOn("detekt", "koverXmlReport")
}
