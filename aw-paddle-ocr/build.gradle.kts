plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.answufeng.paddleocr"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")

        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON",
                    "-DANDROID_STL=c++_static"
                )
            }
        }

        ndk {
            // Google Play 針對 Android 15+ 的 16KB page size 檢查會掃描 APK 內所有 .so。
            // NDK 附帶的 x86_64 `libomp.so` 可能出現 LOAD segment 非 16KB 對齊，導致報警/拒審風險。
            // 本庫面向真機主要 ABI 為 arm，預設僅打包 arm32/arm64；需要模擬器再自行開啟 x86/x86_64。
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/jni/CMakeLists.txt")
        }
    }

    // 讓 AGP 產生可供 maven-publish 使用的 release SoftwareComponent
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
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

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

ktlint {
    android.set(true)
    ignoreFailures = false
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}

apply(from = "${rootDir}/gradle/publish.gradle.kts")
