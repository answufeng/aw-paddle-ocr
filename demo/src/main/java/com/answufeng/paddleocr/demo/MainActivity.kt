package com.answufeng.paddleocr.demo

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.answufeng.paddleocr.AwPaddleOcr
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        findViewById<MaterialButton>(R.id.btnGalleryOcr).setOnClickListener {
            startActivity(Intent(this, GalleryOcrActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnCameraOcr).setOnClickListener {
            startActivity(Intent(this, CameraOcrActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLocalOcr).setOnClickListener {
            GlobalScope.launch {
                try {
                    val bitmap = BitmapFactory.decodeResource(resources, R.mipmap.img01)
                    android.util.Log.i("AwPaddleOcr", "Bitmap size: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}")
                    val result = AwPaddleOcr.detect(bitmap)
                    android.util.Log.i("AwPaddleOcr", "OCR result text: '${result.text}'")
                    android.util.Log.i("AwPaddleOcr", "OCR result textBlocks count: ${result.textBlocks.size}")
                    result.textBlocks.forEachIndexed { index, block ->
                        android.util.Log.i("AwPaddleOcr", "Block[$index]: text='${block.text}', score=${block.boxScore}, crnnTime=${block.crnnTime}")
                    }
                    runOnUiThread {
                        val text = result.text.ifEmpty { "未识别到文字" }
                        findViewById<TextView>(R.id.tvLocalOcrResult).text = text
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AwPaddleOcr", "OCR error", e)
                    runOnUiThread {
                        findViewById<TextView>(R.id.tvLocalOcrResult).text = "识别失败: ${e.message}"
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnMarkOcr).setOnClickListener {
            startActivity(Intent(this, MarkOcrActivity::class.java))
        }
    }
}
