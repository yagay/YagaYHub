pluginManagement {
    repositories {
        google()
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

rootProject.name = "YagaYHub"
include(":app", ":core", ":suite")
include(":ydiag-feature")
project(":ydiag-feature").projectDir = file("features/YDiag/feature")
