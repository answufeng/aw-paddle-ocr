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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
    api(files("libs/OcrLibrary-1.3.0-release.aar"))
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
}

apply(from = "${rootDir}/gradle/publish.gradle.kts")
