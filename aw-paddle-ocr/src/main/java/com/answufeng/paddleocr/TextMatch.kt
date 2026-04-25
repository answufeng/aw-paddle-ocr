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

data class KeyValue(
    val key: String,
    val value: String,
    val keyMatch: TextMatch,
    val valueMatch: TextMatch
)

data class MergedLine(
    val text: String,
    val boxPoint: List<Point>,
    val center: Point,
    val score: Float,
    val blockCount: Int
)

data class TextRegion(
    val boxPoint: List<Point>,
    val center: Point,
    val width: Int,
    val height: Int
)

data class FuzzyMatch(
    val target: String,
    val matched: TextMatch,
    val similarity: Float
)
