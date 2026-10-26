pluginManagement {
    includeBuild("build-logic")
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
        // Tesseract4Android is published through JitPack; only that group is
        // allowed to resolve from it.
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.adaptech-cz.Tesseract4Android")
            }
        }
    }
}

rootProject.name = "FactsWallpaper"

include(":app")

// Core modules
include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:designsystem")
include(":core:ui")
include(":core:security")
include(":core:pdf")
include(":core:ocr")
include(":core:ai")
include(":core:render")
include(":core:wallpaper")
include(":core:backup")

// Feature modules
include(":feature:onboarding")
include(":feature:library")
include(":feature:outline")
include(":feature:facts")
include(":feature:collections")
include(":feature:templates")
include(":feature:wallpaper")
include(":feature:settings")
