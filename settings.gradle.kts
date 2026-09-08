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

// Application module — DI root, Navigation 3 host, route keys (§5)
include(":app")

// Core modules (§5)
include(":core:model")          // pure Kotlin: domain entities, value types, §15 contracts, use cases
include(":core:data")           // Room + BundledSQLiteDriver, FTS5, repositories, file store
include(":core:data:testing")   // pure Kotlin fakes for every §15 interface
include(":core:audio")          // the ONLY module with NDK/CMake/JNI
include(":core:midi")           // MIDI collector, events.log, SMF Type 0 serializer
include(":core:ui")             // semantic tokens, four themes, shared components

// Feature modules — never depend on each other (§5)
include(":feature:capture")
include(":feature:song")
include(":feature:search")
include(":feature:nudges")
include(":feature:export")

// Stage 0 spikes that run on the JVM (§21.1). Device spikes live under docs/spikes.
include(":spikes:s4-fts5")
