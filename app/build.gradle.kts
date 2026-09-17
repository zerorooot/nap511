plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "github.zerorooot.nap511"
    compileSdk = 37


    defaultConfig {
        applicationId = "github.zerorooot.nap511"
        minSdk = 26
        targetSdk = 37
        versionCode = 11
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi"
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.datasource.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)


    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.xlog)
    implementation(files("libs/lazycolumnscrollbar-2.2.0.aar"))
    //implementation(libs.lazycolumnscrollbar)
    implementation(libs.process.phoenix)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.android.request.inspector.webview)
    implementation(libs.compose.zoom)
    implementation(libs.gsyvideoplayer.java)
    implementation(libs.gsyvideoplayer.exo2)
//    implementation(libs.gsyvideoplayer.arm64)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.retrofit)
//json to bean
    implementation(libs.okhttp)
    implementation(libs.converter.gson)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)


    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.serialization.json)
}