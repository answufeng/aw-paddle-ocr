package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmOverloads

/**
 * 标记 [OcrConfig] DSL 作用域，避免在 lambda 中误用外层接收者。
 */
@DslMarker
annotation class AwOcrDsl

/**
 * PP-OCRv5 对外入口（单例 [object]）。
 *
 * 底层 native 在进程内**仅一份**全局状态；[detect]、[detectTextRegionsOnly] 等在与 [detectLock] 相同的关键区
 * 内串行执行，可在多协程/多线程**并发调用**时保证安全，但不应在未 [release] 的情况下假设可并行修改引擎配置。
 * 需更换模型时请先 [release] 或再次 [init]（内部会先释放旧引擎）。
 */
object AwPaddleOcr {

    private const val TAG = "AwPaddleOcr"

    @Volatile
    private var engine: PPOCRv5Engine? = null

    @Volatile
    private var initialized = false

    /**
     * 与 native 检测/识别同一把锁，避免多线程同时进入 C++ 层全局 `PPOCRv5` 状态。
     */
    private val detectLock = Any()

    /** 是否已成功 [init] 且当前持有有效 [PPOCRv5Engine]。 */
    val isInitialized: Boolean get() = initialized

    /**
     * 从 [context] 的 `assets` 加载 PP-OCRv5 检测/识别参数与权重，并初始化单例 native 引擎。
     *
     * 若之前已 [init] 过，会先将旧引擎 [PPOCRv5Engine.release] 再重新加载。
     *
     * @param context 任意 [Context]；仅使用其 [Context.getApplicationContext] 与 [Context.getAssets]，
     *   不长期持有 `Activity` 引用
     * @param modelType 与 `assets` 下 `PP_OCRv5_{modelType}_det.ncnn.*`、`PP_OCRv5_{modelType}_rec.ncnn.*` 命名
     *   中 `{modelType}` 一致，例如 `"mobile"`
     * @param targetSize 检测分支使用的目标边长初值，会与 [config] 中 [OcrConfig.targetSize] 合并后再约束到
     *   320～1280；影响 native `set_target_size` 一类逻辑
     * @param useGpu 是否向 ncnn 申请 Vulkan 推理；设备或驱动不适配时以 CPU 为准（由 native 决定）
     * @param config 可选 [OcrConfig] DSL，仅在**本次**加载模型时生效，用于在默认 [targetSize] 外再设 [OcrConfig.targetSize] 等
     * @return 已连接 native、完成加载的 [PPOCRv5Engine] 实例
     * @throws RuntimeException 当 native 报告加载失败时抛出，消息为「加载 PP-OCRv5 模型失败」
     */
    @JvmOverloads
    fun init(
        context: Context,
        modelType: String = "mobile",
        targetSize: Int = 640,
        useGpu: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): PPOCRv5Engine = synchronized(this) {
        val ocrConfig = OcrConfig().apply {
            targetSize(targetSize)
            config?.invoke(this)
        }
        val appContext = context.applicationContext
        engine?.release()
        engine = null
        initialized = false

        val nativeEngine = PPOCRv5Engine()
        val success = nativeEngine.loadModel(
            appContext.assets,
            modelType = modelType,
            targetSize = ocrConfig.targetSize,
            useGpu = useGpu
        )
        if (!success) {
            throw RuntimeException("加载 PP-OCRv5 模型失败")
        }
        Log.i(TAG, "OCR engine initialized with PP-OCRv5 $modelType model, targetSize=${ocrConfig.targetSize}")
        engine = nativeEngine
        initialized = true
        nativeEngine
    }

    /**
     * 与 [release] 等价。请改用 [release] 表达「释放单例 native 资源」。
     */
    @Deprecated("请使用 release()", replaceWith = ReplaceWith("release()"), level = DeprecationLevel.WARNING)
    fun reset() = release()

    private fun requireEngine(): PPOCRv5Engine {
        return engine ?: throw IllegalStateException("未初始化，请先调用 AwPaddleOcr.init(context)")
    }

    /**
     * 当 [bitmap] 非 [Bitmap.Config.ARGB_8888] 时，转为 ARGB_8888 副本，供 native 使用。
     *
     * @param bitmap 原图，不由本方法回收（若非 ARGB_8888，**调用方**仍只管理传入的那一张；转换出的副本由 [doDetect] 在适当时 [Bitmap.recycle]）
     * @return 可直接交给 native 的位图，可能是 [bitmap] 自身或新分配的副本
     */
    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) {
            return bitmap
        }
        val converted = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(converted)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return converted
    }

    /**
     * 对整幅 [bitmap] 执行一次检测+识别，返回 [OcrResult]。
     *
     * @param bitmap 输入图像，可为任意 [Bitmap.Config]；非 ARGB_8888 时内部会建临时图并在结束后回收
     * @param config 目前**未读取**，仅为与公开 API 签名兼容的占位，不影响推理；实际 `targetSize` 仅在 [init] 中生效
     * @return 排序后的文本块与全量 [OcrResult.text]、耗时等
     */
    private fun doDetect(
        bitmap: Bitmap,
        @Suppress("UNUSED_PARAMETER") config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = synchronized(detectLock) {
        val nativeEngine = requireEngine()
        val converted = bitmap.config != Bitmap.Config.ARGB_8888
        val inputBitmap = ensureArgb8888(bitmap)
        try {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "doDetect: input=${inputBitmap.width}x${inputBitmap.height}, config=${inputBitmap.config}")
            }
            val startTime = System.currentTimeMillis()
            val blocks = nativeEngine.detectAndRecognize(inputBitmap)
            val detectTime = System.currentTimeMillis() - startTime
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "doDetect: found ${blocks.size} text blocks in ${detectTime}ms")
            }
            OcrResult.fromEngine(blocks, detectTime)
        } finally {
            if (converted) {
                inputBitmap.recycle()
            }
        }
    }

    // ==================== 功能1：全量识别 ====================

    /**
     * 全图文字检测与识别。
     *
     * @param bitmap 待识别位图
     * @param config 当前**未参与**推理，仅为 API 兼容保留，见 [doDetect]
     * @return 包含 [OcrResult.text]、[OcrResult.textBlocks] 与 [OcrResult.detectTime] 等
     * @throws IllegalStateException 未先 [init] 时
     */
    @JvmOverloads
    fun detect(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = doDetect(bitmap, config)

    /**
     * 与 [detect] 相同，在 [Dispatchers.Default] 上执行，避免阻塞主线程。
     *
     * @param bitmap 待识别位图
     * @param config 当前**未参与**推理，见 [doDetect]
     * @return 同 [detect]
     */
    suspend fun detectAsync(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        detect(bitmap, config)
    }

    // ==================== 功能2：首个匹配 ====================

    /**
     * 对整图 [detect] 后，在结果中为每个待查字符串取**首块**匹配。
     *
     * 内部会**完整**执行一次 [detect]；对同一张图更推荐 [OcrResult.findFirst]。
     *
     * @param bitmap 待处理图像
     * @param texts 按**顺序**待查找的子串列表，返回列表下标与之一一对应
     * @param ignoreCase 是否对子串匹配使用忽略大小写 [String.contains]
     * @param config 当前**未参与**推理，见 [doDetect]
     * @return 长度与 [texts] 相同；未命中则为 `null`
     */
    @Deprecated(
        "对同一张图建议只调用一次 detect，再用 OcrResult.findFirst 以避免重复推理",
        replaceWith = ReplaceWith("detect(bitmap, config).findFirst(texts, ignoreCase)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findFirst(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch?> = doDetect(bitmap, config).findFirst(texts, ignoreCase)

    /**
     * @param bitmap 待处理图像
     * @param texts 待查找子串列表
     * @param ignoreCase 是否忽略大小写
     * @param config 未参与推理，见 [doDetect]
     * @return 同 [findFirst]
     */
    suspend fun findFirstAsync(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch?> = withContext(Dispatchers.Default) {
        findFirst(bitmap, texts, ignoreCase, config)
    }

    // ==================== 功能3：全部匹配 ====================

    /**
     * 对整图 [detect] 后，按待查词收集**所有**命中的 [TextMatch] 列表。
     *
     * @param bitmap 待处理图像
     * @param texts 查询关键字集合
     * @param ignoreCase 子串 [String.contains] 是否忽略大小写
     * @param config 未参与推理
     * @return 键为 [texts] 中每个词，值为该词命中的块列表
     */
    @Deprecated(
        "对同一张图建议只调用一次 detect，再用 OcrResult.findAll",
        replaceWith = ReplaceWith("detect(bitmap, config).findAll(texts, ignoreCase)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findAll(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<TextMatch>> = doDetect(bitmap, config).findAll(texts, ignoreCase)

    /** @return 同 [findAll] */
    suspend fun findAllAsync(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<TextMatch>> = withContext(Dispatchers.Default) {
        findAll(bitmap, texts, ignoreCase, config)
    }

    // ==================== 功能4：正则匹配 ====================

    /**
     * 对整图 [detect] 后，返回**块内文本**被 [Regex] 命中的 [TextMatch] 列表（[Regex.containsMatchIn]）。
     *
     * @param bitmap 待处理图像
     * @param regex 正则模式字符串
     * @param config 未参与推理
     * @return 命中的各文本块
     */
    @Deprecated(
        "建议使用 detect + OcrResult.findByRegex",
        replaceWith = ReplaceWith("detect(bitmap, config).findByRegex(regex)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findByRegex(
        bitmap: Bitmap,
        regex: String,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch> = doDetect(bitmap, config).findByRegex(regex)

    /**
     * @param bitmap 待处理图像
     * @param regex 正则
     * @param config 未参与推理
     * @return 同 [findByRegex]
     */
    suspend fun findByRegexAsync(
        bitmap: Bitmap,
        regex: String,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch> = withContext(Dispatchers.Default) {
        findByRegex(bitmap, regex, config)
    }

    // ==================== 功能5：配对查找 ====================

    /**
     * 对整图 [detect] 后，在含 [s1] 与 [s2] 的块之间，按**块中心**纵坐标差 [maxHeightDiff] 内两两组合。
     *
     * @param bitmap 待处理图像
     * @param s1 第一个子串
     * @param s2 第二个子串
     * @param maxHeightDiff 两 block 中心点 Y 之差的可接受上界
     * @param ignoreCase 子串匹配是否忽略大小写
     * @param config 未参与推理
     * @return 所有 [TextPair]；可能为空
     */
    @Deprecated(
        "建议使用 detect + OcrResult.findPaired",
        replaceWith = ReplaceWith(
            "detect(bitmap, config).findPaired(s1, s2, maxHeightDiff, ignoreCase)"
        ),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findPaired(
        bitmap: Bitmap,
        s1: String,
        s2: String,
        maxHeightDiff: Int = 10,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextPair> = doDetect(bitmap, config).findPaired(s1, s2, maxHeightDiff, ignoreCase)

    /** @return 同 [findPaired] */
    suspend fun findPairedAsync(
        bitmap: Bitmap,
        s1: String,
        s2: String,
        maxHeightDiff: Int = 10,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextPair> = withContext(Dispatchers.Default) {
        findPaired(bitmap, s1, s2, maxHeightDiff, ignoreCase, config)
    }

    // ==================== 功能6：键值对提取 ====================

    /**
     * 对整图 [detect] 后，在块内/行间提取「键+分隔符+值」结构，见 [OcrResult.extractKeyValues]。
     *
     * @param bitmap 待处理图像
     * @param separators 可视为键值分隔的字符串列表
     * @param maxHeightDiff 多块组合为键-值时允许的纵向容差
     * @param config 未参与推理
     * @return [KeyValue] 列表
     */
    @Deprecated(
        "建议使用 detect + OcrResult.extractKeyValues",
        replaceWith = ReplaceWith("detect(bitmap, config).extractKeyValues(separators, maxHeightDiff)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun extractKeyValues(
        bitmap: Bitmap,
        separators: List<String> = listOf(":", "：", "=", "—"),
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<KeyValue> = doDetect(bitmap, config).extractKeyValues(separators, maxHeightDiff)

    /** @return 同 [extractKeyValues] */
    suspend fun extractKeyValuesAsync(
        bitmap: Bitmap,
        separators: List<String> = listOf(":", "：", "=", "—"),
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<KeyValue> = withContext(Dispatchers.Default) {
        extractKeyValues(bitmap, separators, maxHeightDiff, config)
    }

    // ==================== 功能7：行级合并 ====================

    /**
     * 对整图 [detect] 后，将几何上可视为**同一行**的块合并为 [MergedLine]。
     *
     * @param bitmap 待处理图像
     * @param maxHeightDiff 行聚类时，块中心 Y 与当前行代表 Y 的允许差值
     * @param config 未参与推理
     * @return 合并行列表
     */
    @Deprecated(
        "建议使用 detect + OcrResult.mergeLines",
        replaceWith = ReplaceWith("detect(bitmap, config).mergeLines(maxHeightDiff)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun mergeLines(
        bitmap: Bitmap,
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<MergedLine> = doDetect(bitmap, config).mergeLines(maxHeightDiff)

    /** @return 同 [mergeLines] */
    suspend fun mergeLinesAsync(
        bitmap: Bitmap,
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<MergedLine> = withContext(Dispatchers.Default) {
        mergeLines(bitmap, maxHeightDiff, config)
    }

    // ==================== 功能8：区域识别(ROI) ====================

    /**
     * 仅对 [bitmap] 上 [region] 所裁切的子图做 [doDetect]；**若**裁切后小于全图，会把各块 [TextBlock.boxPoint]
     * 平移回**原图**坐标系。
     *
     * [region] 会夹紧到 [bitmap] 的 `[0, width) × [0, height)`；宽或高为 0 时抛出。
     *
     * @param bitmap 原图
     * @param region 原图坐标系下的 [Rect]（[Rect.left]、[Rect.top]、[Rect.right]、[Rect.bottom]）
     * @param config 未参与推理
     * @return 与全图 [detect] 结构相同的 [OcrResult]；[OcrResult.text] 为裁切区内的多行连接
     * @throws IllegalArgumentException 当裁切后区域无效时
     */
    @JvmOverloads
    fun detectInRegion(
        bitmap: Bitmap,
        region: Rect,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult {
        val left = region.left.coerceAtLeast(0)
        val top = region.top.coerceAtLeast(0)
        val right = region.right.coerceAtMost(bitmap.width)
        val bottom = region.bottom.coerceAtMost(bitmap.height)
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) {
            throw IllegalArgumentException("区域无效: $region，位图 ${bitmap.width}x${bitmap.height}")
        }
        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
        try {
            val rawResult = doDetect(cropped, config)
            if (w < bitmap.width || h < bitmap.height) {
                val offsetBlocks = rawResult.textBlocks.map { block ->
                    val offsetPoints = block.boxPoint.map { p ->
                        Point(p.x + left, p.y + top)
                    }
                    block.copy(boxPoint = offsetPoints)
                }
                return rawResult.copy(textBlocks = offsetBlocks)
            }
            return rawResult
        } finally {
            cropped.recycle()
        }
    }

    /**
     * @param bitmap 原图
     * @param region ROI
     * @param config 未参与推理
     * @return 同 [detectInRegion]
     */
    suspend fun detectInRegionAsync(
        bitmap: Bitmap,
        region: Rect,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        detectInRegion(bitmap, region, config)
    }

    // ==================== 功能9：模糊匹配 ====================

    /**
     * 对整图 [detect] 后，按**编辑距离归一**相似度筛出各目标串的 [FuzzyMatch]（见 [OcrResult.findFuzzy]）。
     *
     * @param bitmap 待处理图像
     * @param texts 要模糊匹配的多个目标词
     * @param minSimilarity 最低相似度 \([0,1]\)
     * @param ignoreCase 计算相似度时是否忽略大小写
     * @param config 未参与推理
     * @return 键为 [texts] 中每个词，值按相似度降序
     */
    @Deprecated(
        "建议使用 detect + OcrResult.findFuzzy",
        replaceWith = ReplaceWith(
            "detect(bitmap, config).findFuzzy(texts, minSimilarity, ignoreCase)"
        ),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findFuzzy(
        bitmap: Bitmap,
        texts: List<String>,
        minSimilarity: Float = 0.6f,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<FuzzyMatch>> =
        doDetect(bitmap, config).findFuzzy(texts, minSimilarity, ignoreCase)

    /** @return 同 [findFuzzy] */
    suspend fun findFuzzyAsync(
        bitmap: Bitmap,
        texts: List<String>,
        minSimilarity: Float = 0.6f,
        ignoreCase: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<FuzzyMatch>> = withContext(Dispatchers.Default) {
        findFuzzy(bitmap, texts, minSimilarity, ignoreCase, config)
    }

    // ==================== 功能10a：仅检测框（不跑识别） ====================

    /**
     * 仅走检测支路，不跑识别（CTC 等），返回各候选框的 [TextRegion]；**无**文字内容。
     *
     * 与 [detect] 共享同一把 [detectLock]，与全图检测/识别互斥、串行执行。
     *
     * @param bitmap 输入位图
     * @param config 当前**未读取**，占位，见 [doDetect]
     * @return 每框 [TextRegion] 的几何与中心
     */
    @JvmOverloads
    fun detectTextRegionsOnly(
        bitmap: Bitmap,
        @Suppress("UNUSED_PARAMETER") config: (OcrConfig.() -> Unit)? = null
    ): List<TextRegion> = synchronized(detectLock) {
        val eng = requireEngine()
        val converted = bitmap.config != Bitmap.Config.ARGB_8888
        val inputBitmap = ensureArgb8888(bitmap)
        try {
            eng.detectTextBlocksOnly(inputBitmap).map { it.toTextRegion() }
        } finally {
            if (converted) {
                inputBitmap.recycle()
            }
        }
    }

    // ==================== 功能10b：由完整 OCR 取块几何（仍执行检测+识别） ====================

    /**
     * 等价于先 [detect] 再 [OcrResult.toTextRegions]，会执行**完整**检测+识别；**不是**仅检测支路。
     * 需要「只框不字」请用 [detectTextRegionsOnly]；已有 [OcrResult] 时直接用 [OcrResult.toTextRegions]。
     *
     * @param bitmap 待处理图像
     * @param config 未参与推理
     * @return 带文字块上的候选框 [TextRegion] 列表
     */
    @Deprecated(
        "若只要框请用 detectTextRegionsOnly；若已有完整识别请用 OcrResult.toTextRegions()",
        replaceWith = ReplaceWith("detectTextRegionsOnly(bitmap, config)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun detectRegions(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextRegion> = doDetect(bitmap, config).toTextRegions()

    /**
     * @param bitmap 输入
     * @param config 未参与推理
     * @return 同 [detectTextRegionsOnly]
     */
    suspend fun detectTextRegionsOnlyAsync(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextRegion> = withContext(Dispatchers.Default) {
        detectTextRegionsOnly(bitmap, config)
    }

    /**
     * @return 同 [detectRegions]
     */
    suspend fun detectRegionsAsync(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextRegion> = withContext(Dispatchers.Default) {
        detectRegions(bitmap, config)
    }

    // ==================== 释放资源 ====================

    /**
     * 释放 native 引擎，并将 [isInitialized] 置为 `false`；之后需再次 [init] 才能识别。
     */
    fun release() = synchronized(this) {
        engine?.release()
        engine = null
        initialized = false
    }

    /**
     * 初始化时用于配置**检测边长**等；**仅**在 [AwPaddleOcr.init] 的 `config` lambda 中或与之合并时生效。
     *
     * 在 [detect]、各 [find]、各 `*Async` 上出现的同类型 `config: (OcrConfig.() -> Unit)?` 为**兼容占位**，
     * 当前**不读**、不参与推理，勿依赖其改变本次调用行为。
     */
    @AwOcrDsl
    class OcrConfig {
        /**
         * 经 [targetSize] 限制在 `[320, 1280]` 后的检测边长，缺省 640；由 [init] 合并入参后写入。
         */
        var targetSize: Int = 640
            private set

        /**
         * 设置 [targetSize]，自动夹紧到 320～1280。
         *
         * @param value 希望使用的边长
         */
        fun targetSize(value: Int) {
            targetSize = value.coerceIn(320, 1280)
        }
    }
}
