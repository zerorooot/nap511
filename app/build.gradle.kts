import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("kotlin-parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun getGitCommitHash(): String {
    return try {
        val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD").start()
        val text = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        text.ifEmpty { "unknown" }
    } catch (_: Throwable) {
        "unknown"
    }
}

android {
    namespace = "github.zerorooot.nap511"
    compileSdk = 37


    defaultConfig {
        applicationId = "github.zerorooot.nap511"
        minSdk = 26
        targetSdk = 37
        versionCode = 14
        versionName = "1.8"
        buildConfigField("String", "GIT_SHA", "\"${getGitCommitHash()}\"")

        val targetAbis = (project.findProperty("ABI_FILTERS") as? String)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
        ndk {
            if (!targetAbis.isNullOrEmpty()) {
                abiFilters.addAll(targetAbis)
            } else {
                abiFilters.add("arm64-v8a")
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        //"en"
        localeFilters += listOf("zh", "zh-rCN")
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            val keystoreProperties = Properties()
            if (keystorePropertiesFile.exists()) {
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
            }

            val storeFilePath = keystoreProperties["storeFile"] as String?
                ?: System.getenv("KEYSTORE_FILE")
            val storePasswordParam = keystoreProperties["storePassword"] as String?
                ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAliasParam = keystoreProperties["keyAlias"] as String?
                ?: System.getenv("KEY_ALIAS")
            val keyPasswordParam = keystoreProperties["keyPassword"] as String?
                ?: System.getenv("KEY_PASSWORD")

            val keystoreFile = storeFilePath?.let { path ->
                val f = file(path)
                if (f.exists()) f else rootProject.file(path)
            }

            if (keystoreFile != null && keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = storePasswordParam
                keyAlias = keyAliasParam
                keyPassword = keyPasswordParam
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            // 如果存在私钥则使用 release 签名，否则回退到 Android 默认的 debug 签名
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/*.version",
                "META-INF/androidx/**/LICENSE.txt",
                "play-services-*.properties",
                "firebase-encoders*.properties",
                "transport-*.properties"
            )
        }
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
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
//    implementation(files("libs/lazycolumnscrollbar-2.2.0.aar"))
    implementation(libs.lazycolumnscrollbar)
    implementation(libs.process.phoenix)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.android.request.inspector.webview)
    implementation(libs.compose.zoom)
    implementation(libs.gsyvideoplayer.java)
    implementation(libs.gsyvideoplayer.exo2) {
        exclude(group = "androidx.media3", module = "media3-cast")
        exclude(group = "io.github.carguo", module = "gsyvideoplayer-rtmp")
    }
//    implementation(libs.gsyvideoplayer.arm64)
    implementation(libs.core.splashscreen)
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
    implementation(libs.google.material)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.transition)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.serialization.json)

    // 局域网扫码与同步依赖
    implementation(libs.zxing.core)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.okhttp.mockwebserver3)


    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

}