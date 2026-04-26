package com.answufeng.paddleocr

import android.graphics.Bitmap
import android.graphics.Point

/**
 * 单张图一次检测+识别（或外部自行构造）的结果容器。
 *
 * @property text 全部 [TextBlock] 的 [TextBlock.text] 经引擎排序后用换行符拼接
 * @property textBlocks 排序后的文本块列表
 * @property detectTime 本次推理耗时（毫秒），双精度
 * @property boxImg 带检测框叠加的位图；**当前实现恒为** `null`，保留供未来或自绘方案
 */
data class OcrResult(
    val text: String,
    val textBlocks: List<TextBlock>,
    val detectTime: Double,
    val boxImg: Bitmap?
) {
    companion object {
        internal fun fromEngine(blocks: List<OcrTextBlock>, detectTimeMs: Long = 0): OcrResult {
            val textBlocks = blocks.map { TextBlock.fromEngine(it) }
            val sortedBlocks = textBlocks.sortedWith(
                compareBy<TextBlock> { it.boxPoint.map { p -> p.y }.min() }
                    .thenBy { it.boxPoint.map { p -> p.x }.min() }
            )
            val fullText = sortedBlocks.joinToString("\n") { it.text }
            return OcrResult(
                text = fullText,
                textBlocks = sortedBlocks,
                detectTime = detectTimeMs.toDouble(),
                boxImg = null
            )
        }
    }
}

/**
 * 面向业务层的单行/单块识别结果；由 [OcrTextBlock] 映射并补充未在 JNI 回传的字段默认值。
 *
 * @property text 该块识别字符串
 * @property boxPoint 四点（通常顺时针）检测框顶点，像素坐标
 * @property boxScore 检测分支给出的该框置信度
 * @property angleIndex 文字方向分类索引，对应 [OcrTextBlock.orientation]
 * @property angleScore 方向分类置信度；当前管线未回传，恒为 `0f`
 * @property angleTime 方向耗时；当前未回传，恒为 `0.0`
 * @property charScores 逐字置信度；当前 native 未填充，多为空数组
 * @property crnnTime 识别分支单块耗时；当前未回传，恒为 `0.0`
 * @property blockTime 单块总耗时；当前未回传，恒为 `0.0`
 */
data class TextBlock(
    val text: String,
    val boxPoint: List<Point>,
    val boxScore: Float,
    val angleIndex: Int,
    val angleScore: Float,
    val angleTime: Double,
    val charScores: FloatArray,
    val crnnTime: Double,
    val blockTime: Double
) {
    companion object {
        internal fun fromEngine(block: OcrTextBlock): TextBlock {
            return TextBlock(
                text = block.text,
                boxPoint = block.boxPoint,
                boxScore = block.boxScore,
                angleIndex = block.orientation,
                angleScore = 0f,
                angleTime = 0.0,
                charScores = FloatArray(0),
                crnnTime = 0.0,
                blockTime = 0.0
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TextBlock
        if (text != other.text) return false
        if (boxPoint != other.boxPoint) return false
        if (boxScore != other.boxScore) return false
        if (angleIndex != other.angleIndex) return false
        if (angleScore != other.angleScore) return false
        if (!charScores.contentEquals(other.charScores)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + boxPoint.hashCode()
        result = 31 * result + boxScore.hashCode()
        result = 31 * result + angleIndex
        result = 31 * result + angleScore.hashCode()
        result = 31 * result + charScores.contentHashCode()
        return result
    }
}
