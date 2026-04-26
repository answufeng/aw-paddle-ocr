package com.answufeng.paddleocr.demo

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.paddleocr.AwPaddleOcr
import com.answufeng.paddleocr.detectFromFile
import com.answufeng.paddleocr.extractKeyValues
import com.answufeng.paddleocr.findByRegex
import com.answufeng.paddleocr.findFirst
import com.answufeng.paddleocr.findFuzzy
import com.answufeng.paddleocr.mergeLines
import com.answufeng.paddleocr.mergedText
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 演示：单图一次 [AwPaddleOcr.detect] 后，对 [com.answufeng.paddleocr.OcrResult] 的扩展链式查询；
 * 以及 [AwPaddleOcr.detectTextRegionsOnly] 仅检测框。
 */
class AdvancedApiActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_advanced_api)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        tvLog = findViewById(R.id.tvLog)
        progressBar = findViewById(R.id.progressBar)

        findViewById<MaterialButton>(R.id.btnRunOcrResultPipeline).setOnClickListener { runOcrResultPipeline() }
        findViewById<MaterialButton>(R.id.btnRunDetectTextOnly).setOnClickListener { runDetectTextOnly() }
        findViewById<MaterialButton>(R.id.btnRunFuzzy).setOnClickListener { runFuzzy() }
        findViewById<MaterialButton>(R.id.btnRunRoi).setOnClickListener { runRoi() }
        findViewById<MaterialButton>(R.id.btnRunRegex).setOnClickListener { runRegex() }
        findViewById<MaterialButton>(R.id.btnRunFromFile).setOnClickListener { runFromFile() }
    }

    private suspend fun loadSampleBitmap() = withContext(Dispatchers.Default) {
        val b = android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.img01)
        b ?: error("未找到资源 img01")
    }

    private fun runOcrResultPipeline() {
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = loadSampleBitmap()
                val t0 = System.currentTimeMillis()
                val result = withContext(Dispatchers.Default) { AwPaddleOcr.detect(bitmap!!) }
                val first = result.findFirst(listOf("识别", "方式"), ignoreCase = true)
                val merged = result.mergeLines(12)
                val kvs = result.extractKeyValues()
                val elapsed = System.currentTimeMillis() - t0
                val sb = StringBuilder()
                sb.appendLine("detect: ${result.detectTime.toInt()} ms (native) / 总 ${elapsed} ms")
                sb.appendLine("块数: ${result.textBlocks.size}")
                sb.appendLine("findFirst(识别,方式): ${first.map { it?.text }}")
                sb.appendLine("mergeLines: ${merged.size} 行, 首行: ${merged.firstOrNull()?.text?.take(40)}")
                sb.appendLine("键值: ${kvs.size} 条, ${kvs.take(3).joinToString { "${it.key}=${it.value}" }}")
                sb.appendLine("--- mergedText(前 200 字) ---\n${result.mergedText.take(200)}")
                tvLog.text = sb.toString()
            } catch (e: Exception) {
                tvLog.text = "失败: ${e.message}"
            } finally {
                bitmap?.recycle()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun runDetectTextOnly() {
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = loadSampleBitmap()
                val t0 = System.currentTimeMillis()
                val regions = withContext(Dispatchers.Default) { AwPaddleOcr.detectTextRegionsOnly(bitmap!!) }
                val elapsed = System.currentTimeMillis() - t0
                tvLog.text = buildString {
                    appendLine("detectTextRegionsOnly: ${elapsed} ms")
                    appendLine("框数: ${regions.size}")
                    appendLine(
                        regions.take(5).joinToString("\n") { r ->
                            "c=${r.center} ${r.width}x${r.height}"
                        }
                    )
                }
            } catch (e: Exception) {
                tvLog.text = "失败: ${e.message}"
            } finally {
                bitmap?.recycle()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun runFuzzy() {
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = loadSampleBitmap()
                val result = withContext(Dispatchers.Default) { AwPaddleOcr.detect(bitmap!!) }
                val fuzzy = result.findFuzzy(
                    listOf("识别方", "别方式"),
                    minSimilarity = 0.5f,
                    ignoreCase = true
                )
                tvLog.text = buildString {
                    appendLine("detect: ${result.detectTime.toInt()} ms, findFuzzy")
                    fuzzy.forEach { (k, v) ->
                        appendLine("$k -> ${v.map { "${it.matched.text}(${it.similarity})" }}")
                    }
                }
            } catch (e: Exception) {
                tvLog.text = "失败: ${e.message}"
            } finally {
                bitmap?.recycle()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun runRoi() {
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = loadSampleBitmap()
                val w = bitmap!!.width
                val h = bitmap.height
                val marginX = (w * 0.15f).toInt()
                val marginY = (h * 0.15f).toInt()
                val region = Rect(marginX, marginY, w - marginX, h - marginY)
                val t0 = System.currentTimeMillis()
                val result = withContext(Dispatchers.Default) {
                    AwPaddleOcr.detectInRegion(bitmap!!, region)
                }
                val elapsed = System.currentTimeMillis() - t0
                tvLog.text = buildString {
                    appendLine("ROI: $region")
                    appendLine("耗时: ${result.detectTime.toInt()} ms (native) / 总 $elapsed ms")
                    appendLine("块数: ${result.textBlocks.size}")
                    appendLine(result.mergedText.take(300))
                }
            } catch (e: Exception) {
                tvLog.text = "失败: ${e.message}"
            } finally {
                bitmap?.recycle()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun runRegex() {
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = loadSampleBitmap()
                val result = withContext(Dispatchers.Default) { AwPaddleOcr.detect(bitmap!!) }
                val nums = result.findByRegex("\\d+")
                tvLog.text = buildString {
                    appendLine("findByRegex \\\\d+ 命中: ${nums.size} 处")
                    nums.take(8).forEach { appendLine(it.text) }
                }
            } catch (e: Exception) {
                tvLog.text = "失败: ${e.message}"
            } finally {
                bitmap?.recycle()
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun runFromFile() {
        progressBar.visibility = View.VISIBLE
        tvLog.text = ""
        lifecycleScope.launch {
            var bitmap: android.graphics.Bitmap? = null
            try {
                bitmap = loadSampleBitmap()
                val file = File(cacheDir, "ocr_demo_temp.png")
                FileOutputStream(file).use { out ->
                    bitmap!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                val t0 = System.currentTimeMillis()
                val result = AwPaddleOcr.detectFromFile(file.absolutePath)
                val elapsed = System.currentTimeMillis() - t0
                tvLog.text = buildString {
                    appendLine("detectFromFile: ${file.absolutePath}")
                    appendLine("native ${result.detectTime.toInt()} ms / 总 $elapsed ms, 块 ${result.textBlocks.size}")
                    appendLine(result.mergedText.take(400))
                }
            } catch (e: Exception) {
                tvLog.text = "失败: ${e.message}"
            } finally {
                bitmap?.recycle()
                progressBar.visibility = View.GONE
            }
        }
    }
}
