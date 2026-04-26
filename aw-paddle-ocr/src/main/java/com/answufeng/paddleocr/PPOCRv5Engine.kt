package com.answufeng.paddleocr

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log

class PPOCRv5Engine {

    private var loaded = false

    val isLoaded: Boolean get() = loaded

    fun loadModel(
        assetManager: AssetManager,
        modelType: String = "mobile",
        targetSize: Int = 640,
        useGpu: Boolean = false
    ): Boolean {
        val result = nativeLoadModel(assetManager, modelType, targetSize, useGpu)
        loaded = result
        if (result) {
            Log.i(TAG, "PPOCRv5 model loaded: type=$modelType, targetSize=$targetSize, useGpu=$useGpu")
        } else {
            Log.e(TAG, "Failed to load PPOCRv5 model")
        }
        return result
    }

    fun detectAndRecognize(bitmap: Bitmap): List<OcrTextBlock> {
        if (!loaded) {
            Log.e(TAG, "Engine not loaded")
            return emptyList()
        }

        val resultStr = nativeDetectAndRecognize(bitmap)
        if (resultStr.isEmpty()) {
            return emptyList()
        }

        return parseResult(resultStr)
    }

    fun release() {
        nativeRelease()
        loaded = false
    }

    private fun parseResult(result: String): List<OcrTextBlock> {
        val blocks = mutableListOf<OcrTextBlock>()
        val lines = result.split("\n")

        for (line in lines) {
            if (line.isEmpty()) continue

            val firstPipe = line.indexOf('|')
            if (firstPipe < 0) continue

            val coordsStr = line.substring(0, firstPipe)
            val rest = line.substring(firstPipe + 1)

            val secondPipe = rest.indexOf('|')
            if (secondPipe < 0) continue

            val scoreStr = rest.substring(0, secondPipe)
            val afterScore = rest.substring(secondPipe + 1)

            val thirdPipe = afterScore.indexOf('|')
            if (thirdPipe < 0) continue

            val orientationStr = afterScore.substring(0, thirdPipe)
            val text = afterScore.substring(thirdPipe + 1)

            val coords = coordsStr.split(",").mapNotNull { it.toFloatOrNull() }
            if (coords.size != 8) continue

            val boxPoint = listOf(
                Point(coords[0].toInt(), coords[1].toInt()),
                Point(coords[2].toInt(), coords[3].toInt()),
                Point(coords[4].toInt(), coords[5].toInt()),
                Point(coords[6].toInt(), coords[7].toInt())
            )

            val score = scoreStr.toFloatOrNull() ?: 0f
            val orientation = orientationStr.toIntOrNull() ?: 0

            blocks.add(OcrTextBlock(text, boxPoint, score, orientation))
        }

        return blocks
    }

    private external fun nativeLoadModel(
        assetManager: AssetManager,
        modelType: String,
        targetSize: Int,
        useGpu: Boolean
    ): Boolean

    private external fun nativeDetectAndRecognize(bitmap: Bitmap): String
    private external fun nativeRelease()

    companion object {
        private const val TAG = "PPOCRv5Engine"

        init {
            System.loadLibrary("aw_ppocrv5")
        }
    }
}

data class OcrTextBlock(
    val text: String,
    val boxPoint: List<Point>,
    val boxScore: Float,
    val orientation: Int
)
