package com.answufeng.paddleocr

import android.graphics.Point

/**
 * 单块文字的查询命中视图：几何 + 检测置信度。
 *
 * @property text 与对应 [TextBlock] 的识别字符串一致
 * @property boxPoint 四点检测框
 * @property center 由框求得的中心点
 * @property score 来自 [TextBlock.boxScore]
 */
data class TextMatch(
    val text: String,
    val boxPoint: List<Point>,
    val center: Point,
    val score: Float
)

/**
 * 一对同时满足纵向距离约束的匹配，见 [OcrResult.findPaired]。
 *
 * @property s1Match [OcrResult.findPaired] 第一个子串对应的 [TextMatch]
 * @property s2Match 第二个子串对应的 [TextMatch]
 * @property heightDiff 两 [TextMatch.center] 的 Y 坐标之差的绝对值
 */
data class TextPair(
    val s1Match: TextMatch,
    val s2Match: TextMatch,
    val heightDiff: Int
)

/**
 * 由键、值两侧 [TextMatch] 组成的键值对，见 [OcrResult.extractKeyValues]。
 *
 * @property key 键字符串
 * @property value 值字符串
 * @property keyMatch 键所在块
 * @property valueMatch 值所在块
 */
data class KeyValue(
    val key: String,
    val value: String,
    val keyMatch: TextMatch,
    val valueMatch: TextMatch
)

/**
 * 将同一「视觉行」上多块合并后的一行。
 *
 * @property text 行内子串 [TextBlock.text] 依阅读顺序 [String] 拼接
 * @property boxPoint 合并后的外接矩形四点
 * @property center 外接框中心
 * @property score 参与合并各块 [TextBlock.boxScore] 的平均
 * @property blockCount 合并自多少个 [TextBlock]
 */
data class MergedLine(
    val text: String,
    val boxPoint: List<Point>,
    val center: Point,
    val score: Float,
    val blockCount: Int
)

/**
 * 仅含几何的文本区域（[detectTextRegionsOnly] 时**无**识别文字；或从块换算）。
 *
 * @property boxPoint 外接多边形（通常为四点）
 * @property center 区域中心
 * @property width 外接水平跨度
 * @property height 外接垂直跨度
 */
data class TextRegion(
    val boxPoint: List<Point>,
    val center: Point,
    val width: Int,
    val height: Int
)

/**
 * 模糊查询的一次命中，见 [OcrResult.findFuzzy]。
 *
 * @property target 查询目标词
 * @property matched 实际命中的块
 * @property similarity 0～1 的归一化相似度
 */
data class FuzzyMatch(
    val target: String,
    val matched: TextMatch,
    val similarity: Float
)
