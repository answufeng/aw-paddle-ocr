package com.answufeng.paddleocr

import android.graphics.Point

/**
 * 针对**已得到的** [OcrResult] 的纯 JVM 后处理，不再次触发 native 推理。
 * 对同一张图应只调一次 [AwPaddleOcr.detect]，再链式使用本文件中的扩展，避免重复全图识别。
 */
/**
 * 对每个待查串在 [textBlocks] 中找**第一块** [String.contains] 命中的文本，包装为 [TextMatch]；未找到为 `null`。
 *
 * @param texts 与返回列表**等长**的查询键，按顺序对应
 * @param ignoreCase 子串匹配是否忽略大小写
 * @return 与 [texts] 等长，元素可为 `null`
 */
fun OcrResult.findFirst(
    texts: List<String>,
    ignoreCase: Boolean = false
): List<TextMatch?> = texts.map { target ->
    textBlocks.firstOrNull { it.text.contains(target, ignoreCase) }?.toTextMatch()
}

/**
 * 为 [texts] 中每个关键字收集**所有**子串 [String.contains] 命中的 [TextBlock]，并包装为 [TextMatch] 列表。
 *
 * @param texts 查询键列表
 * @param ignoreCase 是否忽略大小写
 * @return 以 [texts] 元素为键的映射
 */
fun OcrResult.findAll(
    texts: List<String>,
    ignoreCase: Boolean = false
): Map<String, List<TextMatch>> = texts.associateWith { target ->
    textBlocks.filter { it.text.contains(target, ignoreCase) }.map { it.toTextMatch() }
}

/**
 * 对每块 [TextBlock.text] 用 [Regex.containsMatchIn] 过滤，返回命中的 [TextMatch] 列表。
 *
 * @param regex 正则源字符串
 */
fun OcrResult.findByRegex(regex: String): List<TextMatch> {
    val pattern = Regex(regex)
    return textBlocks.filter { pattern.containsMatchIn(it.text) }.map { it.toTextMatch() }
}

/**
 * 在含 [s1]、[s2] 的块两两之间，若块中心点纵坐标差 [maxHeightDiff] 在允许范围内，则记为 [TextPair]。
 *
 * @param s1 第一个子串
 * @param s2 第二个子串
 * @param maxHeightDiff 两中心点 Y 坐标之差的绝对值上界
 * @param ignoreCase 子串是否忽略大小写
 * @return 可能为空的组合列表
 */
fun OcrResult.findPaired(
    s1: String,
    s2: String,
    maxHeightDiff: Int = 10,
    ignoreCase: Boolean = false
): List<TextPair> {
    val s1Blocks = textBlocks.filter { it.text.contains(s1, ignoreCase) }
    val s2Blocks = textBlocks.filter { it.text.contains(s2, ignoreCase) }
    val pairs = mutableListOf<TextPair>()
    for (s1Block in s1Blocks) {
        val s1Center = s1Block.centerPoint()
        for (s2Block in s2Blocks) {
            val s2Center = s2Block.centerPoint()
            val heightDiff = kotlin.math.abs(s1Center.y - s2Center.y)
            if (heightDiff <= maxHeightDiff) {
                pairs.add(
                    TextPair(
                        s1Match = s1Block.toTextMatch(),
                        s2Match = s2Block.toTextMatch(),
                        heightDiff = heightDiff
                    )
                )
            }
        }
    }
    return pairs
}

/**
 * 在块内/行间解析「键+分隔+值」结构（实现见 [com.answufeng.paddleocr] 包内 `OcrTextProcessing`）。
 *
 * @param separators 候选分隔符，如 `:`、「：」
 * @param maxHeightDiff 多行成对时的纵向容差
 */
fun OcrResult.extractKeyValues(
    separators: List<String> = listOf(":", "：", "=", "—"),
    maxHeightDiff: Int = 10
): List<KeyValue> = extractKeyValuesFromBlocks(this, separators, maxHeightDiff)

/**
 * 按块中心行聚类，将同一条「视觉行」上的 [TextBlock] 合并为 [MergedLine]。
 *
 * @param maxHeightDiff 行聚类时块中心 Y 与当前行代表 Y 的差值上界
 */
fun OcrResult.mergeLines(maxHeightDiff: Int = 10): List<MergedLine> =
    mergeTextBlocksToLines(textBlocks, maxHeightDiff)

/**
 * 为 [texts] 中每个目标词，在块文本上算归一化编辑距离相似度，筛出不低于 [minSimilarity] 的 [FuzzyMatch]。
 *
 * @param texts 待匹配目标
 * @param minSimilarity 最低相似度，范围 0～1
 * @param ignoreCase 比较前是否对两边做 `lowercase`（与内部 [similarityText] 一致）
 * @return 键为 [texts] 元素，值按 [FuzzyMatch.similarity] 降序
 */
fun OcrResult.findFuzzy(
    texts: List<String>,
    minSimilarity: Float = 0.6f,
    ignoreCase: Boolean = false
): Map<String, List<FuzzyMatch>> = texts.associateWith { target ->
    textBlocks.mapNotNull { block ->
        val sim = similarityText(target, block.text, ignoreCase)
        if (sim >= minSimilarity) {
            FuzzyMatch(target = target, matched = block.toTextMatch(), similarity = sim)
        } else {
            null
        }
    }.sortedByDescending { it.similarity }
}

/**
 * 与 [OcrResult.textBlocks] 一一对应，将每块 [TextBlock.boxPoint] 转为 [TextRegion]（外接框与中心）。
 */
fun OcrResult.toTextRegions(): List<TextRegion> = textBlocks.map { it.toTextRegion() }

/** 从 [TextBlock] 的检测框点列构建 [TextRegion]。 */
fun TextBlock.toTextRegion(): TextRegion = boxPointToTextRegion(boxPoint)

/** 与 [toTextRegion] 相同，供引擎块类型使用。 */
fun OcrTextBlock.toTextRegion(): TextRegion = boxPointToTextRegion(boxPoint)

private fun boxPointToTextRegion(boxPoint: List<Point>): TextRegion {
    val xs = boxPoint.map { it.x }
    val ys = boxPoint.map { it.y }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    return TextRegion(
        boxPoint = boxPoint,
        center = Point((minX + maxX) / 2, (minY + maxY) / 2),
        width = maxX - minX,
        height = maxY - minY
    )
}
