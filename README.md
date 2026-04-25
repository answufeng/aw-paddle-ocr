# aw-paddle-ocr

基于 [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) 的 Android 端离线文字识别库，底层使用 [RapidOcrAndroidOnnx](https://github.com/RapidAI/RapidOcrAndroidOnnx) (ONNX Runtime) 推理引擎，内置 **PP-OCRv4** 模型。

## 特性

- 🚀 **离线识别** — 无需网络，本地推理，隐私安全
- 🎯 **高精度** — 内置 PaddleOCR PP-OCRv4 检测+识别模型
- ⚡ **高性能** — ONNX Runtime 推理，速度极快
- 🧩 **易集成** — DSL 配置 + 协程支持，几行代码完成 OCR
- 📦 **开箱即用** — 模型内嵌 AAR，无需额外下载

## 内置模型

| 模型 | 文件名 | 大小 | 说明 |
|------|--------|------|------|
| 检测模型 | ch_PP-OCRv4_det_infer.onnx | ~4.5MB | PP-OCRv4 文本检测 |
| 方向分类 | ch_ppocr_mobile_v2.0_cls_infer.onnx | ~572KB | 文本方向分类 |
| 识别模型 | ch_PP-OCRv4_rec_infer.onnx | ~10MB | PP-OCRv4 文本识别 |
| 字典文件 | ppocr_keys_v1.txt | ~26KB | 中文字符字典 |

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
// 功能1：传入bitmap，返回识别的全部信息
val result = AwPaddleOcr.detect(bitmap)
println(result.mergedText)

// 协程异步识别
lifecycleScope.launch {
    val result = AwPaddleOcr.detectAsync(bitmap)
    println(result.mergedText)
}

// 功能2：传入bitmap和文字列表，返回每个文字的第一个匹配位置
val firstMatches = AwPaddleOcr.findFirst(bitmap, listOf("姓名", "地址"))
// firstMatches[0] -> "姓名"的第一个匹配（TextMatch?），未找到为null
// firstMatches[1] -> "地址"的第一个匹配（TextMatch?），未找到为null

// 功能3：传入bitmap和文字列表，返回每个文字的全部匹配位置
val allMatches = AwPaddleOcr.findAll(bitmap, listOf("姓名", "地址"))
// allMatches["姓名"] -> 所有包含"姓名"的文本块列表
// allMatches["地址"] -> 所有包含"地址"的文本块列表

// 功能4：传入bitmap和正则表达式，返回符合正则的全部匹配位置
val regexMatches = AwPaddleOcr.findByRegex(bitmap, "\\d{11}")
// 返回所有包含11位数字的文本块

// 功能5：传入bitmap和两个文字s1、s2及高度差，返回同一行配对坐标
val pairs = AwPaddleOcr.findPaired(bitmap, "姓名", "张三", maxHeightDiff = 10)
// 返回"姓名"和"张三"中心点Y坐标差值<=10的所有配对

// 自定义参数（所有方法均支持）
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
| `init(context, numThread?, config?)` | 初始化 OCR 引擎（默认加载 PP-OCRv4 模型） |
| `detect(bitmap, config?)` | 功能1：传入bitmap，返回全部识别信息 |
| `detectAsync(bitmap, config?)` | 功能1异步版 |
| `findFirst(bitmap, texts, config?)` | 功能2：传入文字列表，返回每个文字的第一个匹配位置 |
| `findFirstAsync(bitmap, texts, config?)` | 功能2异步版 |
| `findAll(bitmap, texts, config?)` | 功能3：传入文字列表，返回每个文字的全部匹配位置 |
| `findAllAsync(bitmap, texts, config?)` | 功能3异步版 |
| `findByRegex(bitmap, regex, config?)` | 功能4：传入正则表达式，返回符合的全部匹配位置 |
| `findByRegexAsync(bitmap, regex, config?)` | 功能4异步版 |
| `findPaired(bitmap, s1, s2, maxHeightDiff?, config?)` | 功能5：传入两个文字和高度差，返回配对坐标 |
| `findPairedAsync(bitmap, s1, s2, maxHeightDiff?, config?)` | 功能5异步版 |
| `isInitialized` | 是否已初始化 |
| `release()` | 释放引擎资源 |

### TextMatch

| 属性 | 类型 | 说明 |
|------|------|------|
| `text` | String | 匹配到的文本内容 |
| `boxPoint` | List\<Point\> | 文本框四个顶点坐标 |
| `center` | Point | 文本框中心点坐标 |
| `score` | Float | 置信度分数 |

### TextPair

| 属性 | 类型 | 说明 |
|------|------|------|
| `s1Match` | TextMatch | s1 的匹配信息 |
| `s2Match` | TextMatch | s2 的匹配信息 |
| `heightDiff` | Int | 两个文本中心点的Y坐标差值（绝对值） |

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
