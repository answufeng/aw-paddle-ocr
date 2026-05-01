package com.answufeng.paddleocr

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log

/**
 * 底层 **ncnn** 推理与 JNI 桥接。进程内由 [AwPaddleOcr] 持有**单例**；勿在应用代码中 `new`。
 *
 * 由 [AwPaddleOcr.init] 创建并 [loadModel] 后再调用 [detectAndRecognize] 等；弃用时由 [release] 释放 native。
 */
class PPOCRv5Engine internal constructor() {

    @Volatile
    private var loaded = false

    /**
     * 上一次 [loadModel] 是否成功；[release] 后变为 `false`。
     */
    val isLoaded: Boolean get() = loaded

    /**
     * 从 [assetManager] 的 `assets` 中加载与 [modelType] 对应的 `PP_OCRv5_*_det` / `PP_OCRv5_*_rec` 参数与权重。
     *
     * @param assetManager 一般为 [android.content.Context.getAssets]
     * @param modelType 如 `"mobile"`，与 `assets` 中文件名中 `{type}` 一致
     * @param targetSize 检测网输入边长，与 [AwPaddleOcr.init] 中配置的 [AwPaddleOcr.OcrConfig] 一致
     * @param useGpu 是否尝试 Vulkan
     * @return 成功为 `true`；失败为 `false`（[AwPaddleOcr.init] 会将其转为异常）
     */
    fun loadModel(
        assetManager: AssetManager,
        modelType: String = "mobile",
        targetSize: Int = 640,
        useGpu: Boolean = false
    ): Boolean {
        val result = nativeLoadModel(assetManager, modelType, targetSize, useGpu)
        loaded = result
        if (result) {
            Log.i(TAG, "PPOCRv5 model loaded: type=$modelType, targetSize=$targetSize, useGpu=$useGpu")
        } else {
            Log.e(TAG, "Failed to load PPOCRv5 model")
        }
        return result
    }

    /**
     * 对 [bitmap] 做检测 + 识别；要求 [isLoaded] 为真。
     *
     * @param bitmap 须为可交给 JNI 的格式；上层 [AwPaddleOcr] 会归一成 ARGB_8888
     * @return 解析自 native 串的块列表，失败或未加载时为空列表
     */
    fun detectAndRecognize(bitmap: Bitmap): List<OcrTextBlock> {
        if (!loaded) {
            Log.e(TAG, "Engine not loaded")
            return emptyList()
        }

        val resultStr = nativeDetectAndRecognize(bitmap)
        if (resultStr.isEmpty()) {
            return emptyList()
        }

        return OcrEngineResultParser.parseEngineResultString(resultStr)
    }

    /**
     * 仅走检测，不执行识别；[OcrTextBlock.text] 为**空串**，仅用于几何与 [OcrTextBlock.boxScore] 等。
     *
     * @param bitmap 输入位图
     * @return 同 [detectAndRecognize] 的解析方式；未加载或失败时为空
     */
    fun detectTextBlocksOnly(bitmap: Bitmap): List<OcrTextBlock> {
        if (!loaded) {
            Log.e(TAG, "Engine not loaded")
            return emptyList()
        }
        val resultStr = nativeDetectTextBlocksOnly(bitmap)
        if (resultStr.isEmpty()) {
            return emptyList()
        }
        return OcrEngineResultParser.parseEngineResultString(resultStr)
    }

    /**
     * 释放 native 侧 `PPOCRv5` 实例；之后需重新 [loadModel] 才能再次推理。
     */
    fun release() {
        nativeRelease()
        loaded = false
    }

    private external fun nativeLoadModel(
        assetManager: AssetManager,
        modelType: String,
        targetSize: Int,
        useGpu: Boolean
    ): Boolean

    private external fun nativeDetectAndRecognize(bitmap: Bitmap): String

    private external fun nativeDetectTextBlocksOnly(bitmap: Bitmap): String

    private external fun nativeRelease()

    companion object {
        private const val TAG = "PPOCRv5Engine"

        init {
            System.loadLibrary("aw_ppocrv5")
        }
    }
}

/**
 * JNI 经字符串解析后得到的中间块，再映射为 [TextBlock]。
 *
 * @property text 识别结果；[PPOCRv5Engine.detectTextBlocksOnly] 时为空串
 * @property boxPoint 四点多边形顶点
 * @property boxScore 检测置信度
 * @property orientation 方向类索引
 */
data class OcrTextBlock(
    val text: String,
    val boxPoint: List<Point>,
    val boxScore: Float,
    val orientation: Int
)
