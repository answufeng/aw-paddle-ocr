package com.answufeng.paddleocr.demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import androidx.lifecycle.lifecycleScope
import com.answufeng.paddleocr.AwPaddleOcr
import com.answufeng.paddleocr.TextBlock
import kotlinx.coroutines.launch

class MarkOcrActivity : AppCompatActivity() {

    companion object {
        private const val TARGET_TEXT = "签到"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_ocr)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        doMarkOcr()
    }

    private fun doMarkOcr() {
        val progressBar = findViewById<View>(R.id.progressBar)
        val ivResult = findViewById<ImageView>(R.id.ivMarkResult)
        val tvMatchInfo = findViewById<TextView>(R.id.tvMatchInfo)

        progressBar.visibility = View.VISIBLE
        tvMatchInfo.text = "正在识别..."

        lifecycleScope.launch {
            try {
                val originalBitmap = BitmapFactory.decodeResource(resources, R.mipmap.img01)
                val result = AwPaddleOcr.detect(originalBitmap)

                val matchedBlocks = result.textBlocks.filter { it.text.contains(TARGET_TEXT) }

                val markedBitmap = drawMarkOnBitmap(originalBitmap, matchedBlocks)

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    ivResult.setImageBitmap(markedBitmap)

                    if (matchedBlocks.isEmpty()) {
                        tvMatchInfo.text = "未找到 \"$TARGET_TEXT\""
                    } else {
                        val info = matchedBlocks.joinToString("\n") { block ->
                            val center = blockCenter(block)
                            "\"${block.text}\" 位置: (${center.x}, ${center.y})  置信度: ${"%.2f".format(block.boxScore)}"
                        }
                        tvMatchInfo.text = "找到 ${matchedBlocks.size} 个匹配:\n$info"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    tvMatchInfo.text = "识别失败: ${e.message}"
                }
            }
        }
    }

    private fun drawMarkOnBitmap(bitmap: Bitmap, blocks: List<TextBlock>): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val rectPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.RED
            textSize = 36f
            isAntiAlias = true
            setShadowLayer(4f, 1f, 1f, Color.WHITE)
        }

        val textBgPaint = Paint().apply {
            color = Color.parseColor("#B3FF0000")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        for (block in blocks) {
            val pts = block.boxPoint
            if (pts.size == 4) {
                val path = android.graphics.Path()
                path.moveTo(pts[0].x.toFloat(), pts[0].y.toFloat())
                path.lineTo(pts[1].x.toFloat(), pts[1].y.toFloat())
                path.lineTo(pts[2].x.toFloat(), pts[2].y.toFloat())
                path.lineTo(pts[3].x.toFloat(), pts[3].y.toFloat())
                path.close()
                canvas.drawPath(path, rectPaint)

                val label = "\"${block.text}\""
                val textWidth = textPaint.measureText(label)
                val textHeight = textPaint.textSize
                val labelX = pts[0].x.toFloat()
                val labelY = pts[0].y.toFloat() - 8f

                canvas.drawRect(
                    labelX,
                    labelY - textHeight,
                    labelX + textWidth + 8f,
                    labelY + 4f,
                    textBgPaint
                )
                canvas.drawText(label, labelX + 4f, labelY - 4f, textPaint)
            }
        }

        return result
    }

    private fun blockCenter(block: TextBlock): Point {
        val xs = block.boxPoint.map { it.x }
        val ys = block.boxPoint.map { it.y }
        return Point((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2)
    }
}
