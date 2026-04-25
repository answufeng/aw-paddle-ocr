package com.answufeng.paddleocr.demo

import android.app.Application
import com.answufeng.paddleocr.AwPaddleOcr

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AwPaddleOcr.init(this)
    }
}
