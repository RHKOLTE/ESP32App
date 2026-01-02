/**
 * Top-level build file for the project.
 *
 * This file is where you can add configuration options common to all sub-projects/modules.
 * The `plugins` block applies various Gradle plugins to the project, but does not configure them.
 * The actual configuration of these plugins is done in the module-level `build.gradle.kts` files.
 */
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}