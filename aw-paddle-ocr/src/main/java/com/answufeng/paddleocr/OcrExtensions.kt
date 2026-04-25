package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

suspend fun AwPaddleOcr.detectFromFile(
    path: String,
    config: (AwPaddleOcr.OcrConfig.() -> Unit)? = null
): OcrResult = withContext(Dispatchers.IO) {
    val bitmap = BitmapFactory.decodeFile(path)
        ?: throw IllegalArgumentException("Cannot decode bitmap from: $path")
    detectAsync(bitmap, config).also { bitmap.recycle() }
}

suspend fun AwPaddleOcr.detectFromAssets(
    context: Context,
    fileName: String,
    config: (AwPaddleOcr.OcrConfig.() -> Unit)? = null
): OcrResult = withContext(Dispatchers.IO) {
    val inputStream = context.assets.open(fileName)
    val bitmap = BitmapFactory.decodeStream(inputStream)
        ?: throw IllegalArgumentException("Cannot decode bitmap from assets: $fileName")
    inputStream.close()
    detectAsync(bitmap, config).also { bitmap.recycle() }
}

fun OcrResult.saveBoxImageToFile(path: String): Boolean {
    val img = boxImg ?: return false
    return try {
        val file = File(path)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            img.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        true
    } catch (e: Exception) {
        false
    }
}

val OcrResult.lines: List<String>
    get() = textBlocks.map { it.text }

val OcrResult.mergedText: String
    get() = textBlocks.joinToString("\n") { it.text }
