package com.answufeng.paddleocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 从本地路径解码位图并 [AwPaddleOcr.detectAsync]；在 IO 线程解码，**调用方无需**再对返回前位图 [Bitmap.recycle]。
 *
 * @param path 文件系统绝对或相对路径，需可读
 * @param config 当前**未参与**推理（`OcrConfig` 在 [AwPaddleOcr.detect] 上为占位，见 [AwPaddleOcr] 内说明）
 * @return 与 [AwPaddleOcr.detect] 相同结构的 [OcrResult]
 * @throws IllegalArgumentException 当 [BitmapFactory.decodeFile] 失败时
 */
suspend fun AwPaddleOcr.detectFromFile(
    path: String,
    config: (AwPaddleOcr.OcrConfig.() -> Unit)? = null
): OcrResult = withContext(Dispatchers.IO) {
    val bitmap = BitmapFactory.decodeFile(path)
        ?: throw IllegalArgumentException("无法从路径解码位图: $path")
    try {
        detectAsync(bitmap, config)
    } finally {
        bitmap.recycle()
    }
}

/**
 * 从 [Context.getAssets] 打开 [fileName] 并解码为位图后 [AwPaddleOcr.detectAsync]；流在 [use] 内关闭，位图在 `finally` 中回收。
 *
 * @param context 用于访问 `assets`
 * @param fileName `assets` 下相对路径，如 `"ocr/test.jpg"`
 * @param config 当前**未参与**推理（同 [detectFromFile]）
 * @return [OcrResult]
 * @throws IllegalArgumentException 解码失败时
 */
suspend fun AwPaddleOcr.detectFromAssets(
    context: Context,
    fileName: String,
    config: (AwPaddleOcr.OcrConfig.() -> Unit)? = null
): OcrResult = withContext(Dispatchers.IO) {
    context.assets.open(fileName).use { inputStream ->
        val bitmap = BitmapFactory.decodeStream(inputStream)
            ?: throw IllegalArgumentException("无法从 assets 解码位图: $fileName")
        try {
            detectAsync(bitmap, config)
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * 将 [OcrResult.boxImg] 以 PNG 写入 [path]；当前 [OcrResult.boxImg] **恒为** `null`，调用恒为 no-op 并返回 `false`。
 *
 * @param path 目标文件路径，父目录不存在时会尝试创建
 * @return 成功为 `true`；无框图或写出失败为 `false`
 */
@Deprecated("OcrResult.boxImg 目前恒为 null，请改为自行在 Bitmap 上画框后再保存；若未来库内实现框图可移除此弃用。")
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

/**
 * 每个 [TextBlock.text] 组成的列表（顺序与 [OcrResult.textBlocks] 一致），**不**等价于按行合并后的 [mergedText]。
 */
val OcrResult.lines: List<String>
    get() = textBlocks.map { it.text }

/**
 * 将 [textBlocks] 中每块 [TextBlock.text] 用换行符连接，与 [OcrResult.text] 一致（当 [OcrResult] 由引擎构建时）。
 */
val OcrResult.mergedText: String
    get() = textBlocks.joinToString("\n") { it.text }
