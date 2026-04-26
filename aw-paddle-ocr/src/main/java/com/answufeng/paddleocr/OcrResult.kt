package com.answufeng.paddleocr

import android.graphics.Bitmap
import android.graphics.Point

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
