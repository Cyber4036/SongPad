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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SongPad"

// Application module
include(":app")

// Core modules
include(":core:model")
include(":core:data")
include(":core:audio")
include(":core:midi")
include(":core:ui")

// Feature modules
include(":feature:capture")
include(":feature:song")
include(":feature:search")
include(":feature:nudges")
include(":feature:export")
