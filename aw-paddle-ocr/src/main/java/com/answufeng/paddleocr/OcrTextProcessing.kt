package com.answufeng.paddleocr

import android.graphics.Point
import java.util.Locale

internal fun TextBlock.centerPoint(): Point {
    val xs = boxPoint.map { it.x }
    val ys = boxPoint.map { it.y }
    return Point((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
}

internal fun TextBlock.toTextMatch(): TextMatch = TextMatch(
    text = text,
    boxPoint = boxPoint,
    center = centerPoint(),
    score = boxScore
)

internal fun mergeTextBlocksToLines(
    blocks: List<TextBlock>,
    maxHeightDiff: Int
): List<MergedLine> {
    if (blocks.isEmpty()) return emptyList()
    val sorted = blocks.sortedBy { it.centerPoint().y }
    val lines = mutableListOf<MutableList<TextBlock>>()
    var currentLine = mutableListOf(sorted[0])
    lines.add(currentLine)
    for (i in 1 until sorted.size) {
        val block = sorted[i]
        val currentCenterY = currentLine.first().centerPoint().y
        if (kotlin.math.abs(block.centerPoint().y - currentCenterY) <= maxHeightDiff) {
            currentLine.add(block)
        } else {
            currentLine = mutableListOf(block)
            lines.add(currentLine)
        }
    }
    return lines.map { line ->
        val sortedLine = line.sortedBy { it.boxPoint.minOf { p -> p.x } }
        val mergedText = sortedLine.joinToString("") { it.text }
        val allPoints = sortedLine.flatMap { it.boxPoint }
        val minX = allPoints.minOf { it.x }
        val maxX = allPoints.maxOf { it.x }
        val minY = allPoints.minOf { it.y }
        val maxY = allPoints.maxOf { it.y }
        val mergedBox = listOf(
            Point(minX, minY),
            Point(maxX, minY),
            Point(maxX, maxY),
            Point(minX, maxY)
        )
        MergedLine(
            text = mergedText,
            boxPoint = mergedBox,
            center = Point((minX + maxX) / 2, (minY + maxY) / 2),
            score = sortedLine.map { it.boxScore }.average().toFloat(),
            blockCount = sortedLine.size
        )
    }
}

internal fun extractKeyValuesFromBlocks(
    result: OcrResult,
    separators: List<String>,
    maxHeightDiff: Int
): List<KeyValue> {
    val kvList = mutableListOf<KeyValue>()
    val textBlocks = result.textBlocks
    for (block in textBlocks) {
        var bestSep: Pair<Int, String>? = null
        for (sep in separators) {
            if (sep.isEmpty()) continue
            val idx = block.text.indexOf(sep)
            if (idx >= 0 && (bestSep == null || idx < bestSep.first)) {
                bestSep = idx to sep
            }
        }
        if (bestSep != null) {
            val (sepIndex, sep) = bestSep
            val keyText = block.text.substring(0, sepIndex).trim()
            val valueText = block.text.substring(sepIndex + sep.length).trim()
            if (keyText.isNotEmpty()) {
                kvList.add(
                    KeyValue(
                        key = keyText,
                        value = valueText,
                        keyMatch = block.toTextMatch(),
                        valueMatch = block.toTextMatch()
                    )
                )
                continue
            }
        }
        val blockCenter = block.centerPoint()
        val blockRight = block.boxPoint.maxOf { it.x }
        val sameLineBlocks = textBlocks
            .filter { it !== block }
            .filter { other ->
                val otherCenter = other.centerPoint()
                kotlin.math.abs(blockCenter.y - otherCenter.y) <= maxHeightDiff &&
                    other.boxPoint.minOf { it.x } > blockRight - 5
            }
            .sortedBy { it.boxPoint.minOf { p -> p.x } }
        if (sameLineBlocks.isNotEmpty()) {
            val valueBlock = sameLineBlocks.first()
            val keyText = block.text.trim()
            if (keyText.isNotEmpty()) {
                kvList.add(
                    KeyValue(
                        key = keyText,
                        value = valueBlock.text.trim(),
                        keyMatch = block.toTextMatch(),
                        valueMatch = valueBlock.toTextMatch()
                    )
                )
            }
        }
    }
    return kvList
}

internal fun similarityText(s1: String, s2: String, ignoreCase: Boolean): Float {
    val a = if (ignoreCase) s1.lowercase(Locale.ROOT) else s1
    val b = if (ignoreCase) s2.lowercase(Locale.ROOT) else s2
    if (a == b) return 1.0f
    if (a.isEmpty() || b.isEmpty()) return 0.0f
    val maxLen = maxOf(a.length, b.length)
    val dist = levenshteinDistance(a, b)
    return 1.0f - dist.toFloat() / maxLen.toFloat()
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j
    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    return dp[m][n]
}
