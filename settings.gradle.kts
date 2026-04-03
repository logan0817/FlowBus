pluginManagement {
    plugins {
        kotlin("android") version "1.9.23"
        kotlin("jvm") version "1.9.23"
    }

    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
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
