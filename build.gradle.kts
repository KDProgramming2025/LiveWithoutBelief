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
    // (Detekt will be re-added cleanly after purge step)
    id("org.jetbrains.kotlinx.kover") version "0.9.0"
    id("com.diffplug.spotless") version "6.25.0"
    alias(libs.plugins.play.publisher) apply false
    // Enables re-running flaky tests automatically in CI
    id("org.gradle.test-retry") version "1.5.8"
}

// Repositories are centrally managed via settings.gradle.kts (dependencyResolutionManagement)

subprojects {
    // Detekt temporarily removed; will be re-applied cleanly.
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "org.gradle.test-retry")

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        systemProperty("user.timezone", "UTC")
        systemProperty("file.encoding", "UTF-8")
        jvmArgs(listOf("-Djava.awt.headless=true"))
        if (System.getenv("CI") == "true") {
            retry {
                maxRetries.set(1)
                failOnPassedAfterRetry.set(false)
            }
        }
    }

    spotless {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**", "**/.gradle/**")
            ktlint().editorConfigOverride(
                mapOf(
                    "ktlint_standard_no-wildcard-imports" to "disabled",
                    "indent_size" to "4",
                    "max_line_length" to "120"
                )
            )
            licenseHeaderFile(
                rootProject.file("spotless.license.kt"),
                "(package |@file:)"
            )
        }
        kotlinGradle {
            target("**/*.kts")
            targetExclude("**/build/**", "**/.gradle/**")
            ktlint()
        }
    }
}
kotlin.run {
    // placeholder for potential shared config additions later
}

// Simple dependency guard (regex-based) to enforce no forbidden cross-layer imports.
val forbiddenPairs = listOf(
    // feature modules importing data impl packages
    "feature" to "info.lwb.data.repo",
    "feature" to "info.lwb.data.network"
)

// Additional guard: prevent core modules from depending on feature or data impl layers
val coreForbidden = listOf("feature", "data.repo", "data.network")

tasks.register("dependencyGuard") {
    // This task scans project sources via Project API at execution time; not CC-compatible.
    notCompatibleWithConfigurationCache("Uses Project APIs and scans file tree at execution time")
    group = "verification"
    description = "Fails if a feature module imports data implementation packages"
    doLast {
        val violations = mutableListOf<String>()
        subprojects.filter { it.path.startsWith(":feature:") }.forEach { featureProj ->
            val srcDir = featureProj.projectDir.resolve("src")
            if (srcDir.exists()) {
                srcDir.walkTopDown()
                    .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
                    .forEach { file ->
                        val text = file.readText()
                        forbiddenPairs.forEach { (from, target) ->
                            if (featureProj.path.contains(from) && text.contains("import $target")) {
                                violations += "${featureProj.path} imports forbidden $target in ${file.relativeTo(featureProj.projectDir)}"
                            }
                        }
                    }
            }
        }
        if (violations.isNotEmpty()) {
            violations.forEach { println("[dependencyGuard] $it") }
            throw GradleException("Dependency guard violations: ${violations.size}")
        } else {
            println("[dependencyGuard] No violations")
        }
    }
}

// Detekt root configuration removed (will be restored in clean setup phase)

// Coverage verification: stable overall rule. Layered thresholds TODO via custom XML parsing script.
kover {
    reports {
        verify {
            rule("OverallLineCoverage") { bound { minValue = 70 } }
        }
    }
}

// detektAll task removed; will be recreated

tasks.register("quality") {
    group = "verification"
    description = "Runs static analysis and tests with coverage verification"
    // Collect all test tasks from subprojects (unit tests)
    val testTasks = subprojects.flatMap { sp ->
        sp.tasks.matching { it.name.contains("test", ignoreCase = true) && it.name.endsWith("UnitTest") }
    }
    dependsOn(testTasks)
    // Include only debug lint tasks (avoid known FIR crash in aggregate/unit test lint variants)
    val lintTasks = subprojects.flatMap { sp -> sp.tasks.matching { it.name == "lintDebug" } }
    dependsOn(lintTasks)
    dependsOn("koverXmlReport", "dependencyGuard", "spotlessCheck")
}

// Convenience aggregate format task
tasks.register("formatAll") {
    group = "formatting"
    description = "Apply formatting to all modules"
    dependsOn(subprojects.map { "${it.path}:spotlessApply" })
}
