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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "pnifox"

include(
    ":app",
    ":core:model",
    ":core:shared",
    ":core:database",
    ":core:network",
    ":core:vpn",
    ":core:ui",
    ":feature:home",
    ":feature:servers",
    ":feature:scanner",
    ":feature:dns",
    ":feature:mhr",
    ":feature:stats",
    ":feature:settings",
    ":desktop",
)
