plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val enableX86ForEmulator =
    (project.findProperty("aw.ocr.enableX86") as String?)?.toBooleanStrictOrNull() == true

android {
    namespace = "com.answufeng.paddleocr.demo"
    compileSdk = 35
    // NDK r28+ 預設產物為 16KB 對齊（含 libomp），避免 16KB 掃描告警
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.answufeng.paddleocr.demo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // APK 最終打包哪些 ABI 由 app 模組決定；模擬器需要 x86_64 才能載入 JNI .so。
            abiFilters +=
                if (enableX86ForEmulator) {
                    listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
                } else {
                    listOf("armeabi-v7a", "arm64-v8a")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
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

}

dependencies {
    implementation(project(":aw-paddle-ocr"))

    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.coroutines.android)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
}
