package com.answufeng.paddleocr.demo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.answufeng.paddleocr.AwPaddleOcr
import com.answufeng.paddleocr.mergedText
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class GalleryOcrActivity : AppCompatActivity() {

    private lateinit var ivPreview: ImageView
    private lateinit var tvResult: TextView
    private lateinit var tvTime: TextView
    private lateinit var progressBar: ProgressBar

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        val inputStream = contentResolver.openInputStream(uri) ?: return@registerForActivityResult
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        bitmap ?: return@registerForActivityResult
        ivPreview.setImageBitmap(bitmap)
        runOcr(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery_ocr)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ivPreview = findViewById(R.id.ivPreview)
        tvResult = findViewById(R.id.tvResult)
        tvTime = findViewById(R.id.tvTime)
        progressBar = findViewById(R.id.progressBar)

        pickImage.launch("image/*")
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun runOcr(bitmap: Bitmap) {
        progressBar.visibility = ProgressBar.VISIBLE
        tvResult.text = ""
        tvTime.text = ""

        lifecycleScope.launch {
            try {
                val result = AwPaddleOcr.detectAsync(bitmap)
                tvResult.text = result.mergedText
                tvTime.text = "识别耗时: ${result.detectTime.toInt()} ms"
            } catch (e: Exception) {
                tvResult.text = "识别失败: ${e.message}"
            } finally {
                progressBar.visibility = ProgressBar.GONE
            }
        }
    }
}
