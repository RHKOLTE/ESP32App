/**
 * Configures where Gradle looks for plugins.
 */
pluginManagement {
    repositories {
        // Defines the repositories for plugins.
        google {
            content {
                // Specifies that plugins from Google's repository should be used for Android, Google, and AndroidX groups.
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral() // The Maven Central repository.
        gradlePluginPortal() // The official Gradle Plugin Portal.
    }
}

/**
 * Configures how project dependencies are resolved.
 */
dependencyResolutionManagement {
    // Fails the build if a subproject declares its own repositories.
    // This enforces a centralized repository configuration.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Defines the repositories for dependencies.
        google() // Google's Maven repository for Android libraries.
        mavenCentral() // The Maven Central repository.
        maven { url = uri("https://jitpack.io") } // JitPack repository for publishing JVM libraries from Git.
    }
}

/**
 * Sets the root project name and includes the 'app' module in the build.
 */
rootProject.name = "ESP32App"
include(":app")
