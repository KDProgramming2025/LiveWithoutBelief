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
        id("org.jetbrains.kotlin.plugin.spring") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.jpa") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
        id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
        id("org.springframework.boot") version "3.3.2"
        id("io.spring.dependency-management") version "1.1.5"
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
include(":data:repo")
include(":data:network")
include(":feature:reader")
include(":feature:search")
include(":feature:bookmarks")
include(":feature:annotations")
// Backend server (Spring Boot)
include(":server")
