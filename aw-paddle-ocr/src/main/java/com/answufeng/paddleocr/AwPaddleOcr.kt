package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import com.benjaminwan.ocrlibrary.OcrEngine as NativeOcrEngine
import com.benjaminwan.ocrlibrary.OcrResult as NativeOcrResult
import com.benjaminwan.ocrlibrary.TextBlock as NativeTextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmOverloads

@DslMarker
annotation class AwOcrDsl

object AwPaddleOcr {

    private const val TAG = "AwPaddleOcr"

    private const val DET_MODEL = "ch_PP-OCRv4_det_infer.onnx"
    private const val CLS_MODEL = "ch_ppocr_mobile_v2.0_cls_infer.onnx"
    private const val REC_MODEL = "ch_PP-OCRv4_rec_infer.onnx"
    private const val KEYS_FILE = "ppocr_keys_v1.txt"

    @Volatile
    private var engine: NativeOcrEngine? = null

    @Volatile
    private var initialized = false

    val isInitialized: Boolean get() = initialized

    @JvmOverloads
    fun init(
        context: Context,
        numThread: Int = 4,
        config: (OcrConfig.() -> Unit)? = null
    ): NativeOcrEngine = synchronized(this) {
        val ocrConfig = OcrConfig().apply { config?.invoke(this) }
        val appContext = context.applicationContext
        val nativeEngine = NativeOcrEngine(appContext)
        val reinit = nativeEngine.init(
            appContext.assets,
            numThread,
            DET_MODEL,
            CLS_MODEL,
            REC_MODEL,
            KEYS_FILE
        )
        if (!reinit) {
            Log.w(TAG, "Failed to reinitialize with PP-OCRv4 models, using default PP-OCRv3")
        } else {
            Log.i(TAG, "Successfully initialized with PP-OCRv4 models")
        }
        ocrConfig.applyTo(nativeEngine)
        engine = nativeEngine
        initialized = true
        nativeEngine
    }

    private fun requireEngine(): NativeOcrEngine {
        return engine ?: throw IllegalStateException(
            "AwPaddleOcr not initialized. Call AwPaddleOcr.init(context) first."
        )
    }

    private fun doDetect(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)?
    ): OcrResult {
        val ocrConfig = OcrConfig().apply { config?.invoke(this) }
        val nativeEngine = requireEngine()
        ocrConfig.applyTo(nativeEngine)
        val boxImg = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: bitmap
        val nativeResult = nativeEngine.detect(
            bitmap,
            boxImg,
            ocrConfig.padding,
            ocrConfig.maxSideLen,
            ocrConfig.boxScoreThresh,
            ocrConfig.boxThresh,
            ocrConfig.unClipRatio,
            ocrConfig.doAngle,
            ocrConfig.mostAngle
        )
        return OcrResult.fromNative(nativeResult)
    }

    // ==================== 功能1：全量识别 ====================

    @JvmOverloads
    fun detect(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = doDetect(bitmap, config)

    suspend fun detectAsync(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        detect(bitmap, config)
    }

    // ==================== 功能2：首个匹配 ====================

    @JvmOverloads
    fun findFirst(
        bitmap: Bitmap,
        texts: List<String>,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch?> {
        val result = doDetect(bitmap, config)
        return texts.map { target ->
            result.textBlocks
                .firstOrNull { it.text.contains(target) }
                ?.toTextMatch()
        }
    }

    suspend fun findFirstAsync(
        bitmap: Bitmap,
        texts: List<String>,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch?> = withContext(Dispatchers.Default) {
        findFirst(bitmap, texts, config)
    }

    // ==================== 功能3：全部匹配 ====================

    @JvmOverloads
    fun findAll(
        bitmap: Bitmap,
        texts: List<String>,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<TextMatch>> {
        val result = doDetect(bitmap, config)
        return texts.associateWith { target ->
            result.textBlocks
                .filter { it.text.contains(target) }
                .map { it.toTextMatch() }
        }
    }

    suspend fun findAllAsync(
        bitmap: Bitmap,
        texts: List<String>,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<TextMatch>> = withContext(Dispatchers.Default) {
        findAll(bitmap, texts, config)
    }

    // ==================== 功能4：正则匹配 ====================

    @JvmOverloads
    fun findByRegex(
        bitmap: Bitmap,
        regex: String,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch> {
        val result = doDetect(bitmap, config)
        val pattern = Regex(regex)
        return result.textBlocks
            .filter { pattern.containsMatchIn(it.text) }
            .map { it.toTextMatch() }
    }

    suspend fun findByRegexAsync(
        bitmap: Bitmap,
        regex: String,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextMatch> = withContext(Dispatchers.Default) {
        findByRegex(bitmap, regex, config)
    }

    // ==================== 功能5：配对查找 ====================

    @JvmOverloads
    fun findPaired(
        bitmap: Bitmap,
        s1: String,
        s2: String,
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextPair> {
        val result = doDetect(bitmap, config)
        val s1Blocks = result.textBlocks.filter { it.text.contains(s1) }
        val s2Blocks = result.textBlocks.filter { it.text.contains(s2) }
        val pairs = mutableListOf<TextPair>()
        for (s1Block in s1Blocks) {
            val s1Center = s1Block.center()
            for (s2Block in s2Blocks) {
                val s2Center = s2Block.center()
                val heightDiff = kotlin.math.abs(s1Center.y - s2Center.y)
                if (heightDiff <= maxHeightDiff) {
                    pairs.add(
                        TextPair(
                            s1Match = s1Block.toTextMatch(),
                            s2Match = s2Block.toTextMatch(),
                            heightDiff = heightDiff
                        )
                    )
                }
            }
        }
        return pairs
    }

    suspend fun findPairedAsync(
        bitmap: Bitmap,
        s1: String,
        s2: String,
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextPair> = withContext(Dispatchers.Default) {
        findPaired(bitmap, s1, s2, maxHeightDiff, config)
    }

    // ==================== 功能6：键值对提取 ====================

    @JvmOverloads
    fun extractKeyValues(
        bitmap: Bitmap,
        separators: List<String> = listOf(":", "：", "=", "—"),
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<KeyValue> {
        val result = doDetect(bitmap, config)
        val kvList = mutableListOf<KeyValue>()

        for (block in result.textBlocks) {
            val sepIndex = separators.firstNotNullOfOrNull { sep ->
                block.text.indexOf(sep).takeIf { it >= 0 }
            }
            if (sepIndex != null) {
                val sep = block.text.substring(sepIndex, sepIndex + 1)
                val keyText = block.text.substring(0, sepIndex).trim()
                val valueText = block.text.substring(sepIndex + sep.length).trim()
                if (keyText.isNotEmpty()) {
                    kvList.add(
                        KeyValue(
                            key = keyText,
                            value = valueText,
                            keyMatch = block.toTextMatch(),
                            valueMatch = block.toTextMatch()
                        )
                    )
                    continue
                }
            }

            val blockCenter = block.center()
            val blockRight = block.boxPoint.maxOf { it.x }
            val sameLineBlocks = result.textBlocks
                .filter { it !== block }
                .filter { other ->
                    val otherCenter = other.center()
                    kotlin.math.abs(blockCenter.y - otherCenter.y) <= maxHeightDiff &&
                            other.boxPoint.minOf { it.x } > blockRight - 5
                }
                .sortedBy { it.boxPoint.minOf { p -> p.x } }

            if (sameLineBlocks.isNotEmpty()) {
                val valueBlock = sameLineBlocks.first()
                val keyText = block.text.trim()
                if (keyText.isNotEmpty()) {
                    kvList.add(
                        KeyValue(
                            key = keyText,
                            value = valueBlock.text.trim(),
                            keyMatch = block.toTextMatch(),
                            valueMatch = valueBlock.toTextMatch()
                        )
                    )
                }
            }
        }
        return kvList
    }

    suspend fun extractKeyValuesAsync(
        bitmap: Bitmap,
        separators: List<String> = listOf(":", "：", "=", "—"),
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<KeyValue> = withContext(Dispatchers.Default) {
        extractKeyValues(bitmap, separators, maxHeightDiff, config)
    }

    // ==================== 功能7：行级合并 ====================

    @JvmOverloads
    fun mergeLines(
        bitmap: Bitmap,
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<MergedLine> {
        val result = doDetect(bitmap, config)
        return mergeTextBlocks(result.textBlocks, maxHeightDiff)
    }

    suspend fun mergeLinesAsync(
        bitmap: Bitmap,
        maxHeightDiff: Int = 10,
        config: (OcrConfig.() -> Unit)? = null
    ): List<MergedLine> = withContext(Dispatchers.Default) {
        mergeLines(bitmap, maxHeightDiff, config)
    }

    // ==================== 功能8：区域识别(ROI) ====================

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
            throw IllegalArgumentException("Invalid region: $region for bitmap ${bitmap.width}x${bitmap.height}")
        }
        val cropped = Bitmap.createBitmap(bitmap, left, top, w, h)
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
    }

    suspend fun detectInRegionAsync(
        bitmap: Bitmap,
        region: Rect,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        detectInRegion(bitmap, region, config)
    }

    // ==================== 功能9：模糊匹配 ====================

    @JvmOverloads
    fun findFuzzy(
        bitmap: Bitmap,
        texts: List<String>,
        minSimilarity: Float = 0.6f,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<FuzzyMatch>> {
        val result = doDetect(bitmap, config)
        return texts.associateWith { target ->
            result.textBlocks.mapNotNull { block ->
                val sim = similarity(target, block.text)
                if (sim >= minSimilarity) {
                    FuzzyMatch(
                        target = target,
                        matched = block.toTextMatch(),
                        similarity = sim
                    )
                } else null
            }.sortedByDescending { it.similarity }
        }
    }

    suspend fun findFuzzyAsync(
        bitmap: Bitmap,
        texts: List<String>,
        minSimilarity: Float = 0.6f,
        config: (OcrConfig.() -> Unit)? = null
    ): Map<String, List<FuzzyMatch>> = withContext(Dispatchers.Default) {
        findFuzzy(bitmap, texts, minSimilarity, config)
    }

    // ==================== 功能10：纯检测不识别 ====================

    @JvmOverloads
    fun detectRegions(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextRegion> {
        val result = doDetect(bitmap, config)
        return result.textBlocks.map { block ->
            val xs = block.boxPoint.map { it.x }
            val ys = block.boxPoint.map { it.y }
            val minX = xs.min()
            val maxX = xs.max()
            val minY = ys.min()
            val maxY = ys.max()
            TextRegion(
                boxPoint = block.boxPoint,
                center = Point((minX + maxX) / 2, (minY + maxY) / 2),
                width = maxX - minX,
                height = maxY - minY
            )
        }
    }

    suspend fun detectRegionsAsync(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): List<TextRegion> = withContext(Dispatchers.Default) {
        detectRegions(bitmap, config)
    }

    // ==================== 释放 ====================

    fun release() = synchronized(this) {
        engine = null
        initialized = false
    }

    // ==================== 内部工具方法 ====================

    private fun mergeTextBlocks(
        blocks: List<TextBlock>,
        maxHeightDiff: Int
    ): List<MergedLine> {
        if (blocks.isEmpty()) return emptyList()
        val sorted = blocks.sortedBy { it.center().y }
        val lines = mutableListOf<MutableList<TextBlock>>()
        var currentLine = mutableListOf(sorted[0])
        lines.add(currentLine)
        for (i in 1 until sorted.size) {
            val block = sorted[i]
            val currentCenterY = currentLine.first().center().y
            if (kotlin.math.abs(block.center().y - currentCenterY) <= maxHeightDiff) {
                currentLine.add(block)
            } else {
                currentLine = mutableListOf(block)
                lines.add(currentLine)
            }
        }
        return lines.map { line ->
            val sortedLine = line.sortedBy { it.boxPoint.minOf { p -> p.x } }
            val mergedText = sortedLine.joinToString("") { it.text }
            val allPoints = sortedLine.flatMap { it.boxPoint }
            val minX = allPoints.minOf { it.x }
            val maxX = allPoints.maxOf { it.x }
            val minY = allPoints.minOf { it.y }
            val maxY = allPoints.maxOf { it.y }
            val mergedBox = listOf(
                Point(minX, minY),
                Point(maxX, minY),
                Point(maxX, maxY),
                Point(minX, maxY)
            )
            MergedLine(
                text = mergedText,
                boxPoint = mergedBox,
                center = Point((minX + maxX) / 2, (minY + maxY) / 2),
                score = sortedLine.map { it.boxScore }.average().toFloat(),
                blockCount = sortedLine.size
            )
        }
    }

    private fun similarity(s1: String, s2: String): Float {
        if (s1 == s2) return 1.0f
        if (s1.isEmpty() || s2.isEmpty()) return 0.0f
        val maxLen = maxOf(s1.length, s2.length)
        val dist = levenshtein(s1, s2)
        return 1.0f - dist.toFloat() / maxLen.toFloat()
    }

    private fun levenshtein(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    // ==================== OcrConfig ====================

    @AwOcrDsl
    class OcrConfig {
        var padding: Int = 50
            private set

        var maxSideLen: Int = 1024
            private set

        var boxScoreThresh: Float = 0.5f
            private set

        var boxThresh: Float = 0.3f
            private set

        var unClipRatio: Float = 1.6f
            private set

        var doAngle: Boolean = true
            private set

        var mostAngle: Boolean = true
            private set

        fun padding(value: Int) { padding = value.coerceAtLeast(0) }
        fun maxSideLen(value: Int) { maxSideLen = value.coerceAtLeast(320) }
        fun boxScoreThresh(value: Float) { boxScoreThresh = value.coerceIn(0f, 1f) }
        fun boxThresh(value: Float) { boxThresh = value.coerceIn(0f, 1f) }
        fun unClipRatio(value: Float) { unClipRatio = value.coerceAtLeast(0.1f) }
        fun doAngle(enabled: Boolean) { doAngle = enabled }
        fun mostAngle(enabled: Boolean) { mostAngle = enabled }

        internal fun applyTo(engine: NativeOcrEngine) {
            engine.padding = padding
            engine.boxScoreThresh = boxScoreThresh
            engine.boxThresh = boxThresh
            engine.unClipRatio = unClipRatio
            engine.doAngle = doAngle
            engine.mostAngle = mostAngle
        }
    }
}

private fun TextBlock.center(): Point {
    val xs = boxPoint.map { it.x }
    val ys = boxPoint.map { it.y }
    return Point((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
}

private fun TextBlock.toTextMatch(): TextMatch {
    return TextMatch(
        text = text,
        boxPoint = boxPoint,
        center = center(),
        score = boxScore
    )
}
