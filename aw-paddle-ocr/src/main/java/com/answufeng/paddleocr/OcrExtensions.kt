package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun AwPaddleOcr.detectFromFile(
    path: String
): OcrResult = withContext(Dispatchers.IO) {
    val bitmap = BitmapFactory.decodeFile(path)
        ?: throw IllegalArgumentException("无法从路径解码位图: $path")
    try {
        detectAsync(bitmap)
    } finally {
        bitmap.recycle()
    }
}

@Deprecated("config 参数已移除，请使用 detectFromFile(path)", ReplaceWith("detectFromFile(path)"))
suspend fun AwPaddleOcr.detectFromFile(
    path: String,
    config: (AwPaddleOcr.OcrConfig.() -> Unit)?
): OcrResult = detectFromFile(path)

suspend fun AwPaddleOcr.detectFromAssets(
    context: Context,
    fileName: String
): OcrResult = withContext(Dispatchers.IO) {
    context.assets.open(fileName).use { inputStream ->
        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("无法从 assets 解码位图: $fileName")
        try {
            detectAsync(bitmap)
        } finally {
            bitmap.recycle()
        }
    }
}

@Deprecated("config 参数已移除，请使用 detectFromAssets(context, fileName)", ReplaceWith("detectFromAssets(context, fileName)"))
suspend fun AwPaddleOcr.detectFromAssets(
    context: Context,
    fileName: String,
    config: (AwPaddleOcr.OcrConfig.() -> Unit)?
): OcrResult = detectFromAssets(context, fileName)

@Deprecated("OcrResult.boxImg 目前恒为 null，请改为自行在 Bitmap 上画框后再保存")
fun OcrResult.saveBoxImageToFile(path: String): Boolean {
    return false
}

val OcrResult.lines: List<String>
    get() = textBlocks.map { it.text }

val OcrResult.mergedText: String
    get() = textBlocks.joinToString("\n") { it.text }
