// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.paparazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("dev.detekt") version "2.0.0-alpha.0" apply true
    id("org.jetbrains.kotlinx.kover") version "0.9.0"
    id("com.diffplug.spotless") version "6.25.0"
    alias(libs.plugins.play.publisher) apply false
    // Enables re-running flaky tests automatically in CI
    id("org.gradle.test-retry") version "1.5.8"
}

// Repositories are centrally managed via settings.gradle.kts (dependencyResolutionManagement)

subprojects {
    // Always apply Detekt to every included Gradle subproject (all are Kotlin/Android in this repo)
    // Rationale: conditional file-system scanning previously caused some modules to miss plugin application,
    // resulting in detektAll only executing a subset (app + core/*). We need full, consistent coverage.
    apply(plugin = "dev.detekt")

    extensions.configure<dev.detekt.gradle.extensions.DetektExtension>("detekt") {
        buildUponDefaultConfig = true
        config.setFrom(files(rootProject.file("detekt.yml")))
        autoCorrect = false
        parallel = true
    }
    // Attach additional rule set plugins (ktlint wrapper, coroutines). Removed libraries ruleset since project is an app, not a published library.
    dependencies {
        add("detektPlugins", "dev.detekt:detekt-rules-ktlint-wrapper:2.0.0-alpha.0")
        add("detektPlugins", "dev.detekt:detekt-rules-coroutines:2.0.0-alpha.0")
    }
    tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
        jvmTarget.set("17")
        // We want reports even when there are violations so we can aggregate counts.
        // ignoreFailures allows the build to continue after generating reports.
        ignoreFailures = true
        reports {
            sarif.required.set(true)
            html.required.set(true)
            xml.required.set(true)
            md.required.set(false)
            // Explicitly set destination directories to ensure generation
            val reportsDir = project.layout.buildDirectory.dir("reports/detekt").get().asFile
            sarif.outputLocation.set(reportsDir.resolve("${project.name}.sarif"))
            html.outputLocation.set(reportsDir.resolve("${project.name}.html"))
            xml.outputLocation.set(reportsDir.resolve("${project.name}.xml"))
        }
    }
    tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach { jvmTarget.set("17") }
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
            // Removed obsolete exclusion for deleted ReaderTtsSheet.kt placeholder file.
            targetExclude("**/build/**", "**/.gradle/**")
            ktlint().editorConfigOverride(
                mapOf(
                    // Re-enabled rules after indentation cleanup phase completed.
                    // Enforce standard function naming (custom composable naming now aligned with guidance).
                    // Remove previous disables so ktlint applies defaults.
                    // Note: If some Preview/@Composable functions intentionally use PascalCase beyond spec,
                    // consider adding @Suppress or updating names individually instead of globally disabling.
                    "indent_size" to "4",
                    // Reinstate line length guard at 120 chars for readability.
                    "max_line_length" to "120",
                    // Instruct ktlint to skip function-naming checks when annotated with these (Compose style).
                    // This mirrors detekt.yml's FunctionNaming.ignoreAnnotated.
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable,Preview",
                    // Enforce multiline class signature when parameter count >= 2 for readability & consistency
                    "ktlint_class_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "2"
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
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files(rootProject.file("detekt.yml")))
    autoCorrect = false
    parallel = true
}

// Aggregate detekt task removed to improve configuration-cache compatibility

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

// Aggregation task: parses all generated SARIF reports and prints a summary of rule occurrence counts.
// This provides a data-driven prioritization list without disabling any rules or using suppressions.
tasks.register("detektAggregateReport") {
    group = "verification"
    description = "Aggregate detekt SARIF reports into a consolidated rule frequency summary"
    dependsOn("detektAll")
    // Uses direct file IO + dynamic parsing; not CC compatible.
    notCompatibleWithConfigurationCache("Reads generated SARIF reports via File APIs and builds dynamic maps")
    doLast {
        val sarifFiles = subprojects.mapNotNull { sp ->
            sp.layout.buildDirectory.asFile.get().resolve("reports/detekt").listFiles { f -> f.extension == "sarif" }?.toList()
        }.flatten() + rootProject.layout.buildDirectory.asFile.get()
            .resolve("reports/detekt")
            .listFiles { f -> f.extension == "sarif" }
            .orEmpty()

        if (sarifFiles.isEmpty()) {
            logger.warn("[detektAggregateReport] No SARIF files found. Did detekt generate reports?")
            return@doLast
        }
        val ruleRegex = Regex("\"ruleId\"\\s*:\\s*\"([^\"]+)\"")
        val locRegex = Regex("\"physicalLocation\".*?\"artifactLocation\".*?\"uri\"\\s*:\\s*\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
        // Simple split heuristic: capture occurrences sequentially; we pair ruleIds with following physicalLocation blocks.
        data class Finding(val rule: String, val file: String?)
        val findings = mutableListOf<Finding>()
        sarifFiles.forEach { file ->
            val text = file.readText()
            // We iterate through matches; for each rule match we try to find the next physicalLocation after its end.
            var index = 0
            while (true) {
                val ruleMatch = ruleRegex.find(text, index) ?: break
                val ruleId = ruleMatch.groupValues[1]
                val locMatch = locRegex.find(text, ruleMatch.range.last)
                val filePath = locMatch?.groupValues?.get(1)
                findings += Finding(ruleId, filePath)
                index = ruleMatch.range.last + 1
            }
        }
        if (findings.isEmpty()) {
            logger.warn("[detektAggregateReport] No ruleId entries parsed from SARIF files")
            return@doLast
        }
        val counts = findings.groupingBy { it.rule }.eachCount().toMutableMap()
        // Derive category from ruleId pattern: detekt.<category>.<RuleName>
        val categoryRegex = Regex("^detekt\\.([^.]+).*")
        val categoryCounts = mutableMapOf<String, Int>()
        counts.forEach { (rule, c) ->
            val category = categoryRegex.find(rule)?.groupValues?.get(1) ?: "other"
            categoryCounts[category] = (categoryCounts[category] ?: 0) + c
        }
        val outputDir = rootProject.layout.buildDirectory.asFile.get().resolve("reports/detekt")
        outputDir.mkdirs()
        val summaryFile = outputDir.resolve("aggregate-summary.txt")
        summaryFile.printWriter().use { out ->
            out.println("Detekt Aggregate Summary (rule occurrence counts)\n")
            out.println("Categories (descending):")
            categoryCounts.entries.sortedByDescending { it.value }.forEach { (cat, v) ->
                out.println(String.format("  %-20s %6d", cat, v))
            }
            out.println("\nTop 50 Rules (descending):")
            counts.entries.sortedByDescending { it.value }.take(50).forEach { (r, v) ->
                out.println(String.format("  %-40s %6d", r, v))
            }
            // Additional per-file breakdown for Indentation to guide targeted refactors
            val indentationFindings = findings.filter { it.rule == "detekt.ktlint.Indentation" && it.file != null }
            if (indentationFindings.isNotEmpty()) {
                out.println("\nIndentation Hotspots (per-file counts):")
                indentationFindings.groupingBy { it.file!! }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .take(50)
                    .forEach { (file, count) ->
                        out.println(String.format("  %-80s %5d", file, count))
                    }
            }
            out.println("\nTotal findings: ${counts.values.sum()}")
            out.println("SARIF files processed: ${sarifFiles.size}")
        }
        println("[detektAggregateReport] Summary written to ${summaryFile.relativeTo(rootProject.projectDir)}")
    }
}

// ---------------------------------------------------------------------------
// Quality Gate (configuration-cache compatible)
// ---------------------------------------------------------------------------
// Implemented as a typed task (see buildSrc/QualityGateTask) with declared inputs so Gradle can
// safely snapshot task configuration. Sarif file discovery uses lazy providers; no Project
// objects are captured in the task state. This allows reuse of the configuration cache across
// successive builds while enforcing a zero-detekt-findings rule.
// Clean existing detekt report directories before running detektAll to avoid stale or missing SARIF confusion.
// cleanDetektReports removed (not configuration-cache friendly and unnecessary)

tasks.register<QualityGateTask>("qualityGate") {
    group = "verification"
    description = "Fails if detekt findings or formatting violations are present before debug build/install"
    // Depend directly on all detekt tasks so SARIF is produced freshly when needed
    dependsOn(":detekt")
    subprojects.forEach { sp ->
        dependsOn(sp.tasks.named("detekt"))
    }
    // Collect SARIF outputs from root + subprojects (lazy providers)
    val rootSarifDir = layout.buildDirectory.dir("reports/detekt")
    sarifFiles.from(rootSarifDir.map { it.asFileTree.matching { include("*.sarif") } })
    subprojects.forEach { sp ->
        val spSarifDir = sp.layout.buildDirectory.dir("reports/detekt")
        sarifFiles.from(spSarifDir.map { it.asFileTree.matching { include("*.sarif") } })
        sp.tasks.matching { it.name == "spotlessCheck" }.configureEach { dependsOn(this) }
    }
    doLast {
        if (sarifFiles.files.isEmpty()) {
            throw GradleException("qualityGate: No SARIF files produced after detektAll. detekt may have been skipped; failing.")
        }
    }
}

// Attach gate to application assembleDebug / installDebug tasks
subprojects {
    plugins.withId("com.android.application") {
        // Only add dependency if tasks exist (lazy lookup via named/matching)
        tasks.matching { it.name in setOf("assembleDebug", "installDebug") }.configureEach {
            dependsOn(rootProject.tasks.named("qualityGate"))
        }
    }
}

// detektAll removal means we no longer add extra printing logic here

// Ensure every per-module detekt task re-runs (some modules may have been skipped earlier if considered up-to-date)
// Do not mutate detekt tasks with execution-time prints; keep them configuration-cache friendly

