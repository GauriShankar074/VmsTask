// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {

    dependencies {
        //classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin_version")
        classpath(libs.hilt.android.gradle.plugin)
        classpath(libs.secrets.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt)  apply false
    alias(libs.plugins.ksp) apply false
}