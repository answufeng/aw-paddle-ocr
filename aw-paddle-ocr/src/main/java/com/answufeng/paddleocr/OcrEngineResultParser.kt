package com.answufeng.paddleocr

import android.graphics.Point

/**
 * 解析 JNI 层 [PPOCRv5Engine] 返回的字符串，格式为每行一块文字：
 * `x1,y1,...x4,y4|score|orientation|text`
 */
internal object OcrEngineResultParser {

    fun parseEngineResultString(result: String): List<OcrTextBlock> {
        val blocks = mutableListOf<OcrTextBlock>()
        for (line in result.split("\n")) {
            if (line.isEmpty()) continue
            parseLine(line)?.let { blocks.add(it) }
        }
        return blocks
    }

    private fun parseLine(line: String): OcrTextBlock? {
        val firstPipe = line.indexOf('|')
        if (firstPipe < 0) return null
        val coordsStr = line.substring(0, firstPipe)
        val rest = line.substring(firstPipe + 1)

        val secondPipe = rest.indexOf('|')
        if (secondPipe < 0) return null
        val scoreStr = rest.substring(0, secondPipe)
        val afterScore = rest.substring(secondPipe + 1)

        val thirdPipe = afterScore.indexOf('|')
        if (thirdPipe < 0) return null
        val orientationStr = afterScore.substring(0, thirdPipe)
        val text = afterScore.substring(thirdPipe + 1)

        val coords = coordsStr.split(",").mapNotNull { it.toFloatOrNull() }
        if (coords.size != 8) {
            return null
        }

        val boxPoint = listOf(
            Point(coords[0].toInt(), coords[1].toInt()),
            Point(coords[2].toInt(), coords[3].toInt()),
            Point(coords[4].toInt(), coords[5].toInt()),
            Point(coords[6].toInt(), coords[7].toInt())
        )
        val score = scoreStr.toFloatOrNull() ?: 0f
        val orientation = orientationStr.toIntOrNull() ?: 0
        return OcrTextBlock(text, boxPoint, score, orientation)
    }
}
