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
    val score: Float,
    val angleIndex: Int,
    val angleScore: Float,
    val angleLabel: String
) {
    companion object {
        internal fun fromNative(native: NativeTextBlock): TextBlock {
            return TextBlock(
                text = native.text,
                boxPoint = native.boxPoint.map { Point(it.x, it.y) },
                score = native.score,
                angleIndex = native.angleIndex,
                angleScore = native.angleScore,
                angleLabel = native.angleLabel
            )
        }
    }
}
