package com.answufeng.paddleocr

import android.graphics.Point

data class TextMatch(
    val text: String,
    val boxPoint: List<Point>,
    val center: Point,
    val score: Float
)

data class TextPair(
    val s1Match: TextMatch,
    val s2Match: TextMatch,
    val heightDiff: Int
)
