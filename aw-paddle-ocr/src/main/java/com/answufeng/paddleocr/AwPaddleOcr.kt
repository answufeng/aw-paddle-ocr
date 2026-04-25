package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
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

    fun release() = synchronized(this) {
        engine = null
        initialized = false
    }

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
        score = score
    )
}
