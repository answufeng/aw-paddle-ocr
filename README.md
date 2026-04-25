# aw-paddle-ocr

基于 [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) 的 Android 端离线文字识别库，底层使用 [RapidOcrAndroidOnnx](https://github.com/RapidAI/RapidOcrAndroidOnnx) (ONNX Runtime) 推理引擎，支持 PP-OCRv4 模型。

## 特性

- 🚀 **离线识别** — 无需网络，本地推理，隐私安全
- 🎯 **高精度** — 基于 PaddleOCR PP-OCRv4 模型
- ⚡ **高性能** — ONNX Runtime 推理，速度极快
- 🧩 **易集成** — DSL 配置 + 协程支持，几行代码完成 OCR
- 📦 **轻量级** — AAR 集成，无需额外下载模型

## 快速开始

### 1. 添加依赖

在项目 `settings.gradle.kts` 中添加 JitPack 仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

在模块 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-paddle-ocr:1.0.0")
}
```

### 2. 初始化

在 `Application.onCreate()` 中初始化：

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwPaddleOcr.init(this)
    }
}
```

### 3. 识别文字

```kotlin
// 同步识别
val result = AwPaddleOcr.detect(bitmap)
println(result.mergedText)

// 协程异步识别
lifecycleScope.launch {
    val result = AwPaddleOcr.detectAsync(bitmap)
    println(result.mergedText)
}

// 自定义参数
val result = AwPaddleOcr.detect(bitmap) {
    padding(50)
    maxSideLen(1024)
    boxScoreThresh(0.5f)
    boxThresh(0.3f)
    unClipRatio(1.6f)
    doAngle(true)
    mostAngle(true)
}
```

## API

### AwPaddleOcr

| 方法 | 说明 |
|------|------|
| `init(context, config?)` | 初始化 OCR 引擎 |
| `detect(bitmap, config?)` | 同步识别 |
| `detectAsync(bitmap, config?)` | 协程异步识别 |
| `isInitialized` | 是否已初始化 |
| `release()` | 释放引擎资源 |

### OcrConfig (DSL)

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `padding` | 50 | 图像外扩 padding |
| `maxSideLen` | 1024 | 图像长边最大像素 |
| `boxScoreThresh` | 0.5 | 文本框分数阈值 |
| `boxThresh` | 0.3 | 文本框阈值 |
| `unClipRatio` | 1.6 | 文本框扩张比例 |
| `doAngle` | true | 是否启用方向分类 |
| `mostAngle` | true | 方向分类取最多角度 |

### OcrResult

| 属性 | 类型 | 说明 |
|------|------|------|
| `text` | String | 全部识别文本（空格分隔） |
| `textBlocks` | List\<TextBlock\> | 各文本块详情 |
| `detectTime` | Double | 识别耗时（ms） |
| `boxImg` | Bitmap? | 标注了文本框的图像 |
| `mergedText` | String | 按行拼接的识别文本（扩展属性） |
| `lines` | List\<String\> | 各行文本列表（扩展属性） |

### 扩展函数

```kotlin
// 从文件识别
val result = AwPaddleOcr.detectFromFile("/sdcard/test.jpg")

// 从 Assets 识别
val result = AwPaddleOcr.detectFromAssets(context, "test.jpg")

// 保存标注图像
result.saveBoxImageToFile("/sdcard/result.jpg")
```

## 混淆规则

库已内置 `consumer-rules.pro`，无需额外配置。

## 许可证

Apache License 2.0
