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
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // SeeSo/Eyedid eye-tracking SDK. This project sets FAIL_ON_PROJECT_REPOS, so the repo has to
        // be declared here rather than in the root build.gradle.
        maven {
            url = uri("https://seeso.jfrog.io/artifactory/visualcamp-eyedid-sdk-android-release")
        }
    }
}

rootProject.name = "PWDe"
include(":app")
 