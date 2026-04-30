package com.answufeng.paddleocr

import android.graphics.Point

/**
 * 单张图一次检测+识别（或外部自行构造）的结果容器。
 *
 * @property text 全部 [TextBlock] 的 [TextBlock.text] 经引擎排序后用换行符拼接
 * @property textBlocks 排序后的文本块列表
 * @property detectTimeMs 本次推理耗时（毫秒）
 */
data class OcrResult(
    val text: String,
    val textBlocks: List<TextBlock>,
    val detectTimeMs: Long
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
                detectTimeMs = detectTimeMs
            )
        }
    }
}

/**
 * 面向业务层的单行/单块识别结果；由 [OcrTextBlock] 映射。
 *
 * @property text 该块识别字符串
 * @property boxPoint 四点（通常顺时针）检测框顶点，像素坐标
 * @property boxScore 检测分支给出的该框置信度
 * @property angleIndex 文字方向分类索引，对应 [OcrTextBlock.orientation]
 */
data class TextBlock(
    val text: String,
    val boxPoint: List<Point>,
    val boxScore: Float,
    val angleIndex: Int
) {
    companion object {
        internal fun fromEngine(block: OcrTextBlock): TextBlock {
            return TextBlock(
                text = block.text,
                boxPoint = block.boxPoint,
                boxScore = block.boxScore,
                angleIndex = block.orientation
            )
        }
    }
}
