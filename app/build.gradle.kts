plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")


}

android {
    namespace = "github.zerorooot.nap511"
    compileSdk = 37


    defaultConfig {
        applicationId = "github.zerorooot.nap511"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)


    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.xlog)
    implementation(libs.lazycolumnscrollbar)
    implementation(libs.process.phoenix)
    implementation(libs.composeprefs)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.android.request.inspector.webview)
    implementation(libs.accompanist.pager)
    implementation(libs.accompanist.pager.indicators)
    implementation(libs.compose.zoom)
    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.gsyvideoplayer.java)
    implementation(libs.gsyvideoplayer.exo2)
    implementation(libs.gsyvideoplayer.arm64)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.retrofit)
//json to bean
    implementation(libs.okhttp)
    implementation(libs.converter.gson)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)


    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.preference.ktx)


    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.material)
}