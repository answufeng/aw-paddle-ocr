# aw-paddle-ocr

基于 [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) 的 Android 端离线文字识别库，底层使用 [ncnn](https://github.com/Tencent/ncnn) 推理引擎，内置 **PP-OCRv5** 模型。

## 特性

- 🚀 **离线识别** — 无需网络，本地推理，隐私安全
- 🎯 **高精度** — 内置 PaddleOCR PP-OCRv5 检测+识别模型
- ⚡ **高性能** — ncnn 推理，速度极快
- 🧩 **易集成** — DSL 配置 + 协程支持，几行代码完成 OCR
- 📦 **开箱即用** — 模型内嵌 AAR，无需额外下载
- 🔍 **丰富API** — 10种识别模式，覆盖常见OCR场景
- 🎨 **标注识别** — 支持在原图上圈出指定文字位置
- 📱 **16KB 兼容** — 支持 Android 15+ 16KB 页面大小设备

## 内置模型

| 模型 | 文件名 | 大小 | 说明 |
|------|--------|------|------|
| 检测模型 | PP_OCRv5_mobile_det.ncnn.bin/param | ~4MB | PP-OCRv5 文本检测 |
| 识别模型 | PP_OCRv5_mobile_rec.ncnn.bin/param | ~9MB | PP-OCRv5 文本识别 |

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

### 3. 使用示例

#### 功能1：全量识别

```kotlin
val result = AwPaddleOcr.detect(bitmap)
println(result.text) // 按行拼接的识别结果

// 协程异步
val result = AwPaddleOcr.detectAsync(bitmap)
```

#### 功能2：首个匹配

```kotlin
val firstMatches = AwPaddleOcr.findFirst(bitmap, listOf("姓名", "地址"))
// firstMatches[0] -> "姓名"的第一个匹配（TextMatch?），未找到为null
// firstMatches[1] -> "地址"的第一个匹配（TextMatch?），未找到为null
```

#### 功能3：全部匹配

```kotlin
val allMatches = AwPaddleOcr.findAll(bitmap, listOf("姓名", "地址"))
// allMatches["姓名"] -> 所有包含"姓名"的文本块列表
// allMatches["地址"] -> 所有包含"地址"的文本块列表
```

#### 功能4：正则匹配

```kotlin
val regexMatches = AwPaddleOcr.findByRegex(bitmap, "\\d{11}")
// 返回所有包含11位数字的文本块
```

#### 功能5：配对查找

```kotlin
val pairs = AwPaddleOcr.findPaired(bitmap, "姓名", "张三", maxHeightDiff = 10)
// 返回"姓名"和"张三"中心点Y坐标差值<=10的所有配对
```

#### 功能6：键值对提取

```kotlin
// 自动提取 "标签: 值" 或 "标签：值" 格式的键值对
// 也支持同一行中标签在左、值在右的情况
val kvs = AwPaddleOcr.extractKeyValues(bitmap)
kvs.forEach { kv ->
    println("${kv.key} = ${kv.value}")
}

// 自定义分隔符
val kvs = AwPaddleOcr.extractKeyValues(bitmap, separators = listOf(":", "：", "="))
```

#### 功能7：行级合并

```kotlin
// OCR经常把一行文字拆成多个block，mergeLines将同一行的block合并
val lines = AwPaddleOcr.mergeLines(bitmap)
lines.forEach { line ->
    println("${line.text} (合并了${line.blockCount}个块)")
}
```

#### 功能8：区域识别(ROI)

```kotlin
// 只识别图片中指定区域，提升性能
val region = Rect(100, 200, 500, 400)
val result = AwPaddleOcr.detectInRegion(bitmap, region)
// 返回的坐标已自动映射回原图坐标系
```

#### 功能9：模糊匹配

```kotlin
// OCR可能识别有误，用模糊匹配容错查找
val fuzzyMatches = AwPaddleOcr.findFuzzy(bitmap, listOf("姓名", "地址"), minSimilarity = 0.6f)
fuzzyMatches["姓名"]?.forEach { match ->
    println("找到: ${match.matched.text}, 相似度: ${match.similarity}")
}
```

#### 功能10：纯检测不识别

```kotlin
// 只获取文字区域位置，不进行文字识别（节省识别耗时）
val regions = AwPaddleOcr.detectRegions(bitmap)
regions.forEach { region ->
    println("区域: center=${region.center}, ${region.width}x${region.height}")
}
```

#### 功能11：标注识别（Demo）

```kotlin
// 在 Demo 应用中，点击"标注识别"按钮
// 会自动识别 img01 图片并红色方框圈出"识别方式"文字
```

#### 自定义参数

所有方法均支持 OcrConfig DSL：

```kotlin
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
| `init(context, numThread?, config?)` | 初始化 OCR 引擎（默认加载 PP-OCRv5 模型） |
| `detect(bitmap, config?)` | 功能1：全量识别 |
| `detectAsync(bitmap, config?)` | 功能1异步版 |
| `findFirst(bitmap, texts, config?)` | 功能2：每个文字的首个匹配位置 |
| `findFirstAsync(bitmap, texts, config?)` | 功能2异步版 |
| `findAll(bitmap, texts, config?)` | 功能3：每个文字的全部匹配位置 |
| `findAllAsync(bitmap, texts, config?)` | 功能3异步版 |
| `findByRegex(bitmap, regex, config?)` | 功能4：正则匹配的全部位置 |
| `findByRegexAsync(bitmap, regex, config?)` | 功能4异步版 |
| `findPaired(bitmap, s1, s2, maxHeightDiff?, config?)` | 功能5：两个文字的配对坐标 |
| `findPairedAsync(bitmap, s1, s2, maxHeightDiff?, config?)` | 功能5异步版 |
| `extractKeyValues(bitmap, separators?, maxHeightDiff?, config?)` | 功能6：键值对提取 |
| `extractKeyValuesAsync(...)` | 功能6异步版 |
| `mergeLines(bitmap, maxHeightDiff?, config?)` | 功能7：行级合并 |
| `mergeLinesAsync(bitmap, maxHeightDiff?, config?)` | 功能7异步版 |
| `detectInRegion(bitmap, region, config?)` | 功能8：区域识别(ROI) |
| `detectInRegionAsync(bitmap, region, config?)` | 功能8异步版 |
| `findFuzzy(bitmap, texts, minSimilarity?, config?)` | 功能9：模糊匹配 |
| `findFuzzyAsync(bitmap, texts, minSimilarity?, config?)` | 功能9异步版 |
| `detectRegions(bitmap, config?)` | 功能10：纯检测不识别 |
| `detectRegionsAsync(bitmap, config?)` | 功能10异步版 |
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

### KeyValue

| 属性 | 类型 | 说明 |
|------|------|------|
| `key` | String | 键名 |
| `value` | String | 值 |
| `keyMatch` | TextMatch | 键的匹配位置信息 |
| `valueMatch` | TextMatch | 值的匹配位置信息 |

### MergedLine

| 属性 | 类型 | 说明 |
|------|------|------|
| `text` | String | 合并后的文本 |
| `boxPoint` | List\<Point\> | 合并后的文本框坐标 |
| `center` | Point | 文本框中心点 |
| `score` | Float | 平均置信度 |
| `blockCount` | Int | 合并的文本块数量 |

### TextRegion

| 属性 | 类型 | 说明 |
|------|------|------|
| `boxPoint` | List\<Point\> | 文本区域四个顶点坐标 |
| `center` | Point | 区域中心点 |
| `width` | Int | 区域宽度 |
| `height` | Int | 区域高度 |

### FuzzyMatch

| 属性 | 类型 | 说明 |
|------|------|------|
| `target` | String | 查找的目标文字 |
| `matched` | TextMatch | 匹配到的文本块信息 |
| `similarity` | Float | 相似度（0~1，1为完全匹配） |

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
| `text` | String | 全部识别文本（按行拼接，带换行） |
| `textBlocks` | List\<TextBlock\> | 各文本块详情（已按Y/X坐标排序） |
| `detectTime` | Double | 识别耗时（ms） |
| `boxImg` | Bitmap? | 标注了文本框的图像 |

### 扩展函数

```kotlin
// 从文件识别
val result = AwPaddleOcr.detectFromFile("/sdcard/test.jpg")

// 从 Assets 识别
val result = AwPaddleOcr.detectFromAssets(context, "test.jpg")

// 保存标注图像
result.saveBoxImageToFile("/sdcard/result.jpg")
```

## 16KB 设备兼容

本库已配置支持 Android 15+ 16KB 页面大小设备：

- `aaptOptions.noCompress("so")` — 确保 .so 文件不被压缩
- `packaging.jniLibs.useLegacyPackaging = true` — 设置 `extractNativeLibs=true`
- 构建后自动执行 `zipalign -P 16 4` 进行 16KB 对齐

## 混淆规则

库已内置 `consumer-rules.pro`，无需额外配置。

## 许可证

Apache License 2.0