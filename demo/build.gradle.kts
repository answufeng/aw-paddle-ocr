plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.answufeng.paddleocr.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.answufeng.paddleocr.demo"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    aaptOptions {
        noCompress("so")
    }
}

fun androidSdkPath(): String {
    return System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "${System.getProperty("user.home")}/Library/Android/sdk"
}

fun findZipalign(): File? {
    val dir = File(androidSdkPath(), "build-tools")
    return dir.listFiles()
        ?.filter { it.isDirectory }
        ?.maxByOrNull { it.name }
        ?.let { File(it, "zipalign") }
        ?.takeIf { it.exists() }
}

fun alignApk(apkFile: File) {
    val zipalign = findZipalign()
    if (zipalign == null) {
        logger.lifecycle("zipalign not found, skipping 16KB alignment")
        return
    }
    val aligned = File(apkFile.parentFile, apkFile.name + ".aligned")
    logger.lifecycle("Aligning ${apkFile.name} with 16KB page size...")
    exec {
        commandLine(zipalign.absolutePath, "-f", "-P", "16", "4", apkFile.absolutePath, aligned.absolutePath)
    }
    apkFile.delete()
    aligned.renameTo(apkFile)
    logger.lifecycle("16KB alignment done: ${apkFile.absolutePath}")
}

tasks.whenTaskAdded {
    if (name == "packageDebug" || name == "packageRelease") {
        val buildType = if (name == "packageDebug") "debug" else "release"
        val apkName = if (buildType == "debug") "demo-debug.apk" else "demo-release-unsigned.apk"
        doLast {
            val apkFile = File(project.layout.buildDirectory.get().asFile, "outputs/apk/$buildType/$apkName")
            if (apkFile.exists()) {
                alignApk(apkFile)
            }
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
