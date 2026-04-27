pluginManagement {
    repositories {
        // JitPack（CI）環境下避免使用第三方鏡像，降低 502/封鎖風險
        val isJitPack = (System.getenv("JITPACK") ?: "").equals("true", ignoreCase = true)
        if (!isJitPack) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val isJitPack = (System.getenv("JITPACK") ?: "").equals("true", ignoreCase = true)
        if (!isJitPack) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/central") }
        }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "aw-paddle-ocr"

include(":aw-paddle-ocr")
include(":demo")
