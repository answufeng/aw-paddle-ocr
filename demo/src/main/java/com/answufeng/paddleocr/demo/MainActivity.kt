package com.answufeng.paddleocr.demo

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
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
                    val result = AwPaddleOcr.detect(bitmap)
                    runOnUiThread {
                        val text = result.text.ifEmpty { "未识别到文字" }
                        Toast.makeText(this@MainActivity, "识别结果:\n$text", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
