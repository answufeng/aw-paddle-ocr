package com.answufeng.paddleocr

import android.graphics.Bitmap
import android.graphics.Point
import com.benjaminwan.ocrlibrary.OcrResult as NativeOcrResult
import com.benjaminwan.ocrlibrary.TextBlock as NativeTextBlock

data class OcrResult(
    val text: String,
    val textBlocks: List<TextBlock>,
    val detectTime: Double,
    val boxImg: Bitmap?
) {
    companion object {
        internal fun fromNative(native: NativeOcrResult): OcrResult {
            return OcrResult(
                text = native.strRes,
                textBlocks = native.textBlocks.map { TextBlock.fromNative(it) },
                detectTime = native.detectTime,
                boxImg = native.boxImg
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
        internal fun fromNative(native: NativeTextBlock): TextBlock {
            return TextBlock(
                text = native.text,
                boxPoint = native.boxPoint.map { Point(it.x, it.y) },
                boxScore = native.boxScore,
                angleIndex = native.angleIndex,
                angleScore = native.angleScore,
                angleTime = native.angleTime,
                charScores = native.charScores,
                crnnTime = native.crnnTime,
                blockTime = native.blockTime
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
