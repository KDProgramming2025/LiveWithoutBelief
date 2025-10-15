pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    }
}
// Enable JDK toolchain auto-provisioning on developer machines
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LiveWithoutBelief"
include(":app")
include(":core:model")
include(":core:common")
include(":core:domain")
include(":data:repo")
include(":data:network")
include(":feature:reader")
include(":feature:search")
include(":feature:annotations")
include(":feature:articles")
include(":feature:home")
include(":feature:settings")

// Benchmark module (macrobenchmark + baseline profile)
include(":benchmark")

// Test utilities and UI design system
include(":core:test-fixtures")
include(":ui:design-system")
