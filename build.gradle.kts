// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Changed to a version that should be available in the repositories
    id("com.google.gms.google-services") version "4.4.1" apply false
}