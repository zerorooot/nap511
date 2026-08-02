// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    //依赖检测./gradlew buildHealth --rerun-tasks
    id("com.autonomousapps.dependency-analysis") version "3.18.0"
}