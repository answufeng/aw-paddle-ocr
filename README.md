# aw-paddle-ocr

基于 [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) 的 Android 离线文字识别库，底层 [ncnn](https://github.com/Tencent/ncnn)，内置 **PP-OCRv5** 检测与识别模型（随 AAR 打包）。

**当前发布版本**：`1.0.7`（通过 [JitPack](https://jitpack.io/#answufeng/aw-paddle-ocr/1.0.7) 按 tag 拉取）。

**建议阅读顺序**：完成「依赖」与「初始化」后，**优先**阅读「推荐用法：一次检测，多次后处理」；需要按场景查代码时再展开「使用示例」。

## 特性

- **离线**：无需网络，端侧推理
- **易集成**：Kotlin 协程、初始化时 `OcrConfig` 配置 `targetSize`
- **能力完整**：全图识别、ROI、键值对、模糊/正则匹配、行合并、仅检测框等
- **省推理**：同一张图推荐只调一次 `detect`，再对 `OcrResult` 做后处理；只要框、不要字用 **`detectTextRegionsOnly`**
- **16KB 页面**：需 **AGP 8.5.1+**（本仓库为 8.6.x），由 AGP 处理 JNI 对齐，见下文「16KB 页面大小」

## 内置模型

`modelType = "mobile"` 时，将下列文件放在 **`assets/`** 根目录（与命名一致）：

| 能力 | 文件名 |
|------|--------|
| 检测 | `PP_OCRv5_mobile_det.ncnn.param` / `PP_OCRv5_mobile_det.ncnn.bin` |
| 识别 | `PP_OCRv5_mobile_rec.ncnn.param` / `PP_OCRv5_mobile_rec.ncnn.bin` |

其他 `modelType` 时，将文件名中的 `mobile` 换为对应类型即可（与 [初始化](#初始化) 中 `modelType` 一致）。

## 依赖

在 `settings.gradle.kts` 的仓库中增加 [JitPack](https://jitpack.io)（与 `google()`、`mavenCentral()` 并列即可）：

```kotlin
maven { url = uri("https://jitpack.io") }
```

```kotlin
dependencies {
    implementation("com.github.answufeng:aw-paddle-ocr:1.0.7")
}
```

也可将 `1.0.7` 换为其它 [tag](https://github.com/answufeng/aw-paddle-ocr/tags) 或 commit hash；构建结果以 [JitPack 构建页](https://jitpack.io/#answufeng/aw-paddle-ocr) 为准。

## 初始化

在 `Application.onCreate()`（或首次使用 OCR 前）调用 **`AwPaddleOcr.init`**。`targetSize`、`OcrConfig` **仅在加载模型时**生效。

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwPaddleOcr.init(
            this,
            modelType = "mobile",
            targetSize = 640,
            useGpu = false
        ) {
            // 可选：与入参 targetSize 二选一再覆盖
            // targetSize(800)
        }
    }
}
```

| 参数 | 说明 |
|------|------|
| `modelType` | 与 `assets` 中 `PP_OCRv5_{modelType}_det/rec.*` 一致 |
| `targetSize` | 检测输入边长相关尺度，范围见 `OcrConfig`（默认 640，可调 320～1280） |
| `useGpu` | 是否走 ncnn Vulkan；设备不支持时回退 CPU |

## 推荐用法：一次检测，多次后处理

对**同一张位图**若要做多类分析（关键词、键值、合并行、模糊匹配等），请只调用 **一次** `AwPaddleOcr.detect(bitmap)`，再对 `OcrResult` 使用扩展方法（**不会**再次进入 native 推理）：

```kotlin
val result = AwPaddleOcr.detect(bitmap)
val firsts = result.findFirst(listOf("姓名", "电话"), ignoreCase = true)
val merged = result.mergeLines(maxHeightDiff = 10)
val kvs = result.extractKeyValues()
val fuzzy = result.findFuzzy(listOf("姓名"), minSimilarity = 0.65f, ignoreCase = true)
val boxes = result.toTextRegions() // 带文字块的几何信息
```

`AwPaddleOcr.findFirst(bitmap, …)` 等**以 bitmap 为入参的便捷方法**仍可用，但内部**每次都会整图 `detect` 一次**，已标 **@Deprecated**，适合快速迁移、不适合「一图多查」。

## 使用示例

下面对应「按场景直调 `AwPaddleOcr`」的写法；**生产环境**更推荐上一节的 **`detect` + 扩展**。

### 功能1：全量识别

```kotlin
val result = AwPaddleOcr.detect(bitmap)
println(result.text) // 多行以换行拼接
```

```kotlin
val result = AwPaddleOcr.detectAsync(bitmap) // 协程
```

### 功能2：首个匹配

```kotlin
val firstMatches = AwPaddleOcr.findFirst(bitmap, listOf("姓名", "地址"))
// firstMatches[0] / [1] 为对应关键字的 TextMatch?，未找到为 null
```

### 功能3：全部匹配

```kotlin
val allMatches = AwPaddleOcr.findAll(bitmap, listOf("姓名", "地址"))
// allMatches["姓名"]、["地址"] 为各关键字命中的块列表
```

### 功能4：正则匹配

```kotlin
val regexMatches = AwPaddleOcr.findByRegex(bitmap, "\\d{11}")
```

### 功能5：配对查找

```kotlin
val pairs = AwPaddleOcr.findPaired(bitmap, "姓名", "张三", maxHeightDiff = 10)
```

### 功能6：键值对提取

```kotlin
val kvs = AwPaddleOcr.extractKeyValues(bitmap)
kvs.forEach { kv -> println("${kv.key} = ${kv.value}") }

val kvs2 = AwPaddleOcr.extractKeyValues(bitmap, separators = listOf(":", "：", "="))
```

### 功能7：行级合并

```kotlin
val lines = AwPaddleOcr.mergeLines(bitmap)
lines.forEach { line ->
    println("${line.text}（${line.blockCount} 个块）")
}
```

### 功能8：区域识别（ROI）

```kotlin
import android.graphics.Rect

val region = Rect(100, 200, 500, 400) // left, top, right, bottom
val result = AwPaddleOcr.detectInRegion(bitmap, region) // 坐标已映射回原图
```

### 功能9：模糊匹配

```kotlin
val fuzzyMatches = AwPaddleOcr.findFuzzy(bitmap, listOf("姓名", "地址"), minSimilarity = 0.6f)
fuzzyMatches["姓名"]?.forEach { match ->
    println("找到: ${match.matched.text}, 相似度: ${match.similarity}")
}
```

### 功能10：纯检测、不识别

只取文字框、不跑识别（省 CTC 等耗时）用 **`detectTextRegionsOnly`**：

```kotlin
val regions = AwPaddleOcr.detectTextRegionsOnly(bitmap)
regions.forEach { region ->
    println("区域: center=${region.center}, ${region.width}x${region.height}")
}
```

`detectRegions(bitmap)` 仍保留，但已 **@Deprecated**：实现为**完整检测+识别**后取框，与「仅检测」语义不同。只要框、不要字请用 `detectTextRegionsOnly`；若已有全量结果，用 `detect(bitmap).toTextRegions()`。

### 功能11：标注识别（Demo）

Demo 中「标注识别」会加载 `img01` 并以红框标出「识别方式」等，实现见 `demo` 模块。

### 从文件 / Assets

`com.answufeng.paddleocr` 包下扩展（`OcrExtensions.kt`），**suspend**：

```kotlin
val r = AwPaddleOcr.detectFromFile(path)
val r2 = AwPaddleOcr.detectFromAssets(context, "test.jpg")
```

`OcrResult.boxImg` 目前恒为 `null`；`saveBoxImageToFile` 已弃用，需自绘检测框后自行保存位图。

## API 速览

| 入口 | 作用 |
|------|------|
| `init` / `release` / `isInitialized` | 加载与释放（单例 native 引擎） |
| `detect` / `detectAsync` | 全图检测+识别 |
| `detectInRegion` / `detectInRegionAsync` | 指定矩形 ROI，坐标回映射原图 |
| `detectTextRegionsOnly` / `detectTextRegionsOnlyAsync` | 仅检测框，无文字内容 |
| `findFirst` / `findAll` / `findByRegex` / `findPaired` / `extractKeyValues` / `mergeLines` / `findFuzzy` 及 `*Async` | 以 **bitmap** 为入参时，内部会 `detect`；建议改用 `OcrResult` 扩展 |
| `detectRegions` | **已弃用**；请用 `detectTextRegionsOnly` 或 `detect().toTextRegions()` |
| `reset` | **已弃用**；请用 `release()` |

`OcrResult` 上扩展（不二次推理）：`findFirst`、`findAll`、`findByRegex`、`findPaired`、`extractKeyValues`、`mergeLines`、`findFuzzy`、`toTextRegions`。

`OcrConfig`：仅 `targetSize`，在 **`init` 的 DSL** 中生效。各 `detect` / 便捷方法上的 `OcrConfig` 参数**保留签名为兼容，当前不参与推理**。

## 16KB 页面大小（Android 15+）

- 使用 **AGP 8.5.1+**
- 使用较新 NDK 及面向 16KB 的链接/打包方式（本库 CMake 已考虑相关项）
- **ABI 说明**：为避免 `x86_64/libomp.so` 在部分环境出现 **LOAD segment 非 16KB 对齐**导致告警/拒审，库默认仅打包 `armeabi-v7a` / `arm64-v8a`；如需模拟器再自行启用 `x86` / `x86_64`。

详见官方文档 [支持 16KB 页面](https://developer.android.com/guide/practices/page-sizes)。

## 模拟器（x86_64）运行

从 `1.0.7` 起，为了规避 `x86_64/libomp.so` 在部分环境的 **16KB 对齐**问题，库默认不打包 `x86/x86_64`，因此在 **x86_64 模拟器**上会出现找不到 `libaw_ppocrv5.so` 的错误。

如需在模拟器上运行，请在构建时临时开启 x86：

```bash
./gradlew :demo:installDebug -Paw.ocr.enableX86=true
```

## 混淆

AAR 已带 `consumer-rules.pro`；若 JNI 仍被 R8 裁剪，可补充：

```pro
-keep class com.answufeng.paddleocr.PPOCRv5Engine { *; }
```

## 许可

[Apache License 2.0](LICENSE)
