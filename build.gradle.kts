// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied by :app only when a google-services.json is present, so the project
    // still builds and runs (in device-only mode) before Firebase is hooked up.
    alias(libs.plugins.google.services) apply false
}
