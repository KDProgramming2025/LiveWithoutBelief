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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LiveWithoutBelief"
include(":app")
// Android core + feature modules
include(":core:model")
include(":core:common")
include(":core:domain")
include(":data:repo")
include(":data:network")
include(":feature:reader")
include(":feature:search")
include(":feature:bookmarks")
include(":feature:annotations")

// Benchmark module (macrobenchmark + baseline profile)
include(":benchmark")
