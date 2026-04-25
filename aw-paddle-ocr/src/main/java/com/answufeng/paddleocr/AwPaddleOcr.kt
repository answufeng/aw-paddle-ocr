package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import com.benjaminwan.ocrlibrary.OcrEngine as NativeOcrEngine
import com.benjaminwan.ocrlibrary.OcrResult as NativeOcrResult
import com.benjaminwan.ocrlibrary.TextBlock as NativeTextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmOverloads

@DslMarker
annotation class AwOcrDsl

object AwPaddleOcr {

    @Volatile
    private var engine: NativeOcrEngine? = null

    @Volatile
    private var initialized = false

    val isInitialized: Boolean get() = initialized

    fun init(context: Context, config: (OcrConfig.() -> Unit)? = null): NativeOcrEngine = synchronized(this) {
        val ocrConfig = OcrConfig().apply { config?.invoke(this) }
        val nativeEngine = NativeOcrEngine(context.applicationContext)
        engine = nativeEngine
        initialized = true
        nativeEngine
    }

    private fun requireEngine(): NativeOcrEngine {
        return engine ?: throw IllegalStateException(
            "AwPaddleOcr not initialized. Call AwPaddleOcr.init(context) first."
        )
    }

    @JvmOverloads
    fun detect(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult {
        val ocrConfig = OcrConfig().apply { config?.invoke(this) }
        val nativeEngine = requireEngine()
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

    suspend fun detectAsync(
        bitmap: Bitmap,
        config: (OcrConfig.() -> Unit)? = null
    ): OcrResult = withContext(Dispatchers.Default) {
        detect(bitmap, config)
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
    }
}
