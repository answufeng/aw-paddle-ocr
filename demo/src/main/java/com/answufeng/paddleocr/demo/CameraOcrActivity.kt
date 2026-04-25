package com.answufeng.paddleocr.demo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.answufeng.paddleocr.AwPaddleOcr
import com.answufeng.paddleocr.mergedText
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class CameraOcrActivity : AppCompatActivity() {

    private lateinit var ivCapture: ImageView
    private lateinit var tvResult: TextView
    private lateinit var tvTime: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCapture: MaterialButton

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var latestBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_ocr)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ivCapture = findViewById(R.id.ivCapture)
        tvResult = findViewById(R.id.tvResult)
        tvTime = findViewById(R.id.tvTime)
        progressBar = findViewById(R.id.progressBar)
        btnCapture = findViewById(R.id.btnCapture)

        btnCapture.setOnClickListener {
            latestBitmap?.let { runOcr(it) }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            tvResult.text = "需要相机权限才能使用此功能"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(findViewById<androidx.camera.view.PreviewView>(R.id.previewView).surfaceProvider)

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        latestBitmap = imageProxy.toBitmap()
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                tvResult.text = "相机启动失败: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postRotate(imageInfo.rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraProvider?.unbindAll()
    }
}
