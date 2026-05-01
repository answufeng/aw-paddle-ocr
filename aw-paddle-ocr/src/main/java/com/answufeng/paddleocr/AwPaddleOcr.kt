package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmOverloads

@DslMarker
annotation class AwOcrDsl

/**
 * OCR 性能监听器，每次检测/识别完成后回调，可用于日志、统计或上报。
 */
interface OcrPerformanceListener {
    /**
     * @param inputWidth 输入图宽
     * @param inputHeight 输入图高
     * @param blockCount 识别到的文本块数
     * @param detectTimeMs 推理耗时（毫秒）
     */
    fun onDetect(inputWidth: Int, inputHeight: Int, blockCount: Int, detectTimeMs: Long)
}

/**
 * PP-OCRv5 对外入口（单例 [object]）。
 *
 * 底层 native 在进程内**仅一份**全局状态；[init]、[release]、[detect]、[detectTextRegionsOnly] 等均与
 * [detectLock] 同一把锁串行执行，可在多协程/多线程**并发调用**时保证安全。
 * 需更换模型时请先 [release] 或再次 [init]（内部会先释放旧引擎）。
 */
object AwPaddleOcr {

    private const val TAG = "AwPaddleOcr"

    @Volatile
    private var engine: PPOCRv5Engine? = null

    @Volatile
    private var initialized = false

    private val detectLock = Any()

    @Volatile
    private var performanceListener: OcrPerformanceListener? = null

    val isInitialized: Boolean get() = initialized

    fun setPerformanceListener(listener: OcrPerformanceListener?) {
        performanceListener = listener
    }

    @JvmOverloads
    fun init(
        context: Context,
        modelType: String = "mobile",
        targetSize: Int = 640,
        useGpu: Boolean = false,
        config: (OcrConfig.() -> Unit)? = null
    ): PPOCRv5Engine = synchronized(detectLock) {
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

    @Deprecated("请使用 release()", replaceWith = ReplaceWith("release()"), level = DeprecationLevel.WARNING)
    fun reset() = release()

    private fun requireEngine(): PPOCRv5Engine {
        return engine ?: throw IllegalStateException("未初始化，请先调用 AwPaddleOcr.init(context)")
    }

    private fun ensureArgb8888(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) {
            return bitmap
        }
        val converted = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(converted)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return converted
    }

    private fun doDetect(bitmap: Bitmap): OcrResult = synchronized(detectLock) {
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
            performanceListener?.onDetect(inputBitmap.width, inputBitmap.height, blocks.size, detectTime)
            OcrResult.fromEngine(blocks, detectTime)
        } finally {
            if (converted) {
                inputBitmap.recycle()
            }
        }
    }

    // ==================== 功能1：全量识别 ====================

    fun detect(bitmap: Bitmap): OcrResult = doDetect(bitmap)

    suspend fun detectAsync(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        detect(bitmap)
    }

    // ==================== 功能2：首个匹配 ====================

    @Deprecated(
        "对同一张图建议只调用一次 detect，再用 OcrResult.findFirst 以避免重复推理",
        replaceWith = ReplaceWith("detect(bitmap).findFirst(texts, ignoreCase)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findFirst(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false
    ): List<TextMatch?> = doDetect(bitmap).findFirst(texts, ignoreCase)

    suspend fun findFirstAsync(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false
    ): List<TextMatch?> = withContext(Dispatchers.Default) {
        findFirst(bitmap, texts, ignoreCase)
    }

    // ==================== 功能3：全部匹配 ====================

    @Deprecated(
        "对同一张图建议只调用一次 detect，再用 OcrResult.findAll",
        replaceWith = ReplaceWith("detect(bitmap).findAll(texts, ignoreCase)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findAll(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false
    ): Map<String, List<TextMatch>> = doDetect(bitmap).findAll(texts, ignoreCase)

    suspend fun findAllAsync(
        bitmap: Bitmap,
        texts: List<String>,
        ignoreCase: Boolean = false
    ): Map<String, List<TextMatch>> = withContext(Dispatchers.Default) {
        findAll(bitmap, texts, ignoreCase)
    }

    // ==================== 功能4：正则匹配 ====================

    @Deprecated(
        "建议使用 detect + OcrResult.findByRegex",
        replaceWith = ReplaceWith("detect(bitmap).findByRegex(regex)"),
        level = DeprecationLevel.WARNING
    )
    fun findByRegex(bitmap: Bitmap, regex: String): List<TextMatch> =
        doDetect(bitmap).findByRegex(regex)

    suspend fun findByRegexAsync(bitmap: Bitmap, regex: String): List<TextMatch> =
        withContext(Dispatchers.Default) {
            findByRegex(bitmap, regex)
        }

    // ==================== 功能5：配对查找 ====================

    @Deprecated(
        "建议使用 detect + OcrResult.findPaired",
        replaceWith = ReplaceWith("detect(bitmap).findPaired(s1, s2, maxHeightDiff, ignoreCase)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findPaired(
        bitmap: Bitmap,
        s1: String,
        s2: String,
        maxHeightDiff: Int = 10,
        ignoreCase: Boolean = false
    ): List<TextPair> = doDetect(bitmap).findPaired(s1, s2, maxHeightDiff, ignoreCase)

    suspend fun findPairedAsync(
        bitmap: Bitmap,
        s1: String,
        s2: String,
        maxHeightDiff: Int = 10,
        ignoreCase: Boolean = false
    ): List<TextPair> = withContext(Dispatchers.Default) {
        findPaired(bitmap, s1, s2, maxHeightDiff, ignoreCase)
    }

    // ==================== 功能6：键值对提取 ====================

    @Deprecated(
        "建议使用 detect + OcrResult.extractKeyValues",
        replaceWith = ReplaceWith("detect(bitmap).extractKeyValues(separators, maxHeightDiff)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun extractKeyValues(
        bitmap: Bitmap,
        separators: List<String> = listOf(":", "：", "=", "—"),
        maxHeightDiff: Int = 10
    ): List<KeyValue> = doDetect(bitmap).extractKeyValues(separators, maxHeightDiff)

    suspend fun extractKeyValuesAsync(
        bitmap: Bitmap,
        separators: List<String> = listOf(":", "：", "=", "—"),
        maxHeightDiff: Int = 10
    ): List<KeyValue> = withContext(Dispatchers.Default) {
        extractKeyValues(bitmap, separators, maxHeightDiff)
    }

    // ==================== 功能7：行级合并 ====================

    @Deprecated(
        "建议使用 detect + OcrResult.mergeLines",
        replaceWith = ReplaceWith("detect(bitmap).mergeLines(maxHeightDiff)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun mergeLines(
        bitmap: Bitmap,
        maxHeightDiff: Int = 10
    ): List<MergedLine> = doDetect(bitmap).mergeLines(maxHeightDiff)

    suspend fun mergeLinesAsync(
        bitmap: Bitmap,
        maxHeightDiff: Int = 10
    ): List<MergedLine> = withContext(Dispatchers.Default) {
        mergeLines(bitmap, maxHeightDiff)
    }

    // ==================== 功能8：区域识别(ROI) ====================

    fun detectInRegion(bitmap: Bitmap, region: Rect): OcrResult {
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
            val rawResult = doDetect(cropped)
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

    suspend fun detectInRegionAsync(bitmap: Bitmap, region: Rect): OcrResult =
        withContext(Dispatchers.Default) {
            detectInRegion(bitmap, region)
        }

    // ==================== 功能9：模糊匹配 ====================

    @Deprecated(
        "建议使用 detect + OcrResult.findFuzzy",
        replaceWith = ReplaceWith("detect(bitmap).findFuzzy(texts, minSimilarity, ignoreCase)"),
        level = DeprecationLevel.WARNING
    )
    @JvmOverloads
    fun findFuzzy(
        bitmap: Bitmap,
        texts: List<String>,
        minSimilarity: Float = 0.6f,
        ignoreCase: Boolean = false
    ): Map<String, List<FuzzyMatch>> =
        doDetect(bitmap).findFuzzy(texts, minSimilarity, ignoreCase)

    suspend fun findFuzzyAsync(
        bitmap: Bitmap,
        texts: List<String>,
        minSimilarity: Float = 0.6f,
        ignoreCase: Boolean = false
    ): Map<String, List<FuzzyMatch>> = withContext(Dispatchers.Default) {
        findFuzzy(bitmap, texts, minSimilarity, ignoreCase)
    }

    // ==================== 功能10a：仅检测框（不跑识别） ====================

    fun detectTextRegionsOnly(bitmap: Bitmap): List<TextRegion> = synchronized(detectLock) {
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

    @Deprecated(
        "若只要框请用 detectTextRegionsOnly；若已有完整识别请用 OcrResult.toTextRegions()",
        replaceWith = ReplaceWith("detectTextRegionsOnly(bitmap)"),
        level = DeprecationLevel.WARNING
    )
    fun detectRegions(bitmap: Bitmap): List<TextRegion> = doDetect(bitmap).toTextRegions()

    suspend fun detectTextRegionsOnlyAsync(bitmap: Bitmap): List<TextRegion> =
        withContext(Dispatchers.Default) {
            detectTextRegionsOnly(bitmap)
        }

    suspend fun detectRegionsAsync(bitmap: Bitmap): List<TextRegion> =
        withContext(Dispatchers.Default) {
            detectRegions(bitmap)
        }

    // ==================== 释放资源 ====================

    fun release() = synchronized(detectLock) {
        engine?.release()
        engine = null
        initialized = false
    }

    @AwOcrDsl
    class OcrConfig {
        var targetSize: Int = 640
            private set

        fun targetSize(value: Int) {
            val v = if (value <= 0) 640 else value
            targetSize = v.coerceIn(320, 1280)
        }
    }
}
