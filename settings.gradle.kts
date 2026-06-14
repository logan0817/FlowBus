pluginManagement {
    plugins {
        kotlin("android") version "1.9.25"
        kotlin("jvm") version "1.9.25"
    }

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = ("FlowBus")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "app",
    "flowbus-core",
    "library-android"
)
