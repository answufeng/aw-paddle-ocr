package com.answufeng.paddleocr.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

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
    }
}
